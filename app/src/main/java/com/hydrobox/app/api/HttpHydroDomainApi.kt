package com.hydrobox.app.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.util.UUID

class HttpHydroDomainApi(
    private val baseUrl: String,
    private val contextProvider: suspend () -> DomainApiContext,
    private val onUnauthorized: suspend () -> Unit = {},
    private val onAuthenticatedSuccess: suspend () -> Unit = {},
    private val responseCache: DomainResponseCache = NoOpDomainResponseCache,
    private val cacheScopeProvider: () -> String? = { null },
    private val now: () -> Instant = Instant::now,
    private val uuid: () -> UUID = UUID::randomUUID,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    }
) : HydroDomainApi {
    private val mutableDataStatus = MutableStateFlow(DomainDataStatus())
    override val dataStatus: StateFlow<DomainDataStatus> = mutableDataStatus

    override suspend fun sensors(): List<ApiSensor> =
        list("catalogs/sensors") { value ->
            ApiSensor(
                sensorKey = value.requireAllowedKey("sensor_key", HydroApiContract.sensorKeys),
                name = value.requireString("name"),
                unitSymbol = value.requireString("unit_symbol"),
                description = value.nullableString("description"),
                displayOrder = value.requirePositiveInt("display_order"),
                active = value.requireBoolean("is_active")
            )
        }

    override suspend fun actuators(): List<ApiActuator> = list("catalogs/actuators") { value ->
        val state = value.getJSONObject("state")
        ApiActuator(
            actuatorKey = value.requireAllowedKey("actuator_key", HydroApiContract.actuatorKeys),
            name = value.requireString("name"),
            actuatorType = value.requireString("actuator_type"),
            safeState = value.requireBinaryState("safe_state"),
            active = value.requireBoolean("is_active"),
            state = ApiActuatorState(
                desiredState = state.requireBinaryState("desired_state"),
                reportedState = state.nullableBinaryState("reported_state"),
                availabilityKey = state.requireAllowedKey("availability_key", HydroApiContract.availabilityKeys),
                desiredChangedAt = state.nullableInstant("desired_changed_at"),
                reportedAt = state.nullableInstant("reported_at"),
                lastSeenAt = state.nullableInstant("last_seen_at")
            )
        )
    }

    override suspend fun nutrients(): List<ApiNutrient> = list("catalogs/nutrients") { value ->
        ApiNutrient(
            nutrientKey = value.requireAllowedKey("nutrient_key", HydroApiContract.nutrientKeys),
            name = value.requireString("name"),
            dosingActuatorKey = value.requireAllowedKey("dosing_actuator_key", HydroApiContract.actuatorKeys),
            description = value.nullableString("description"),
            active = value.requireBoolean("is_active")
        )
    }

    override suspend fun crops(): List<ApiCrop> = list("catalogs/crops") { value ->
        ApiCrop(
            cropKey = value.requireAllowedKey("crop_key", HydroApiContract.cropKeys),
            name = value.requireString("name"),
            description = value.nullableString("description"),
            defaultCycleDays = value.nullablePositiveInt("default_cycle_days"),
            active = value.requireBoolean("is_active")
        )
    }

    override suspend fun sensorRanges(cropKey: String): List<ApiSensorRange> {
        requireCropKey(cropKey)
        return list("catalogs/crops/$cropKey/sensor-ranges") { value ->
            ApiSensorRange(
                sensorKey = value.requireAllowedKey("sensor_key", HydroApiContract.sensorKeys),
                unitSymbol = value.requireString("unit_symbol"),
                minValue = value.requireFiniteNumber("min_value"),
                maxValue = value.requireFiniteNumber("max_value")
            ).also {
                if (it.minValue > it.maxValue) throw invalidResponse()
            }
        }
    }

    override suspend fun activeCycle(): ApiCycle? {
        val context = contextProvider()
        return activeCycle(context, allowCache = true)
    }

    override suspend fun changeActiveCrop(
        cropKey: String,
        plannedDurationDays: Int?
    ): ApiCycle {
        requireCropKey(cropKey)
        if (plannedDurationDays != null && plannedDurationDays < 1) throw invalidResponse()

        val context = contextProvider()
        val current = activeCycle(context, allowCache = false)
        if (current?.cropKey == cropKey) return current

        val transitionAt = now().toString()
        if (current != null) {
            request(
                context = context,
                method = "PATCH",
                path = "cycles/${current.cycleUuid}",
                body = JSONObject().apply {
                    put("ended_at", transitionAt)
                    put("is_active", false)
                }
            ).requireDataObject().toCycle()
            invalidateCachedReads(setOf("cycles?"))
        }

        val cycleUuid = uuid().toString()
        val created = requestIdempotent(
            context = context,
            method = "POST",
            path = "cycles",
            body = JSONObject().apply {
                put("cycle_uuid", cycleUuid)
                put("crop_key", cropKey)
                put("started_at", transitionAt)
                put("planned_duration_days", plannedDurationDays ?: JSONObject.NULL)
                put("is_active", true)
                put("notes", JSONObject.NULL)
            },
            idempotencyKey = cycleUuid
        ).requireDataObject().toCycle()
        invalidateCachedReads(setOf("cycles?", "telemetry/measurements"))
        return created
    }

    override suspend fun measurements(
        limit: Int,
        cursor: String?,
        capturedFrom: Instant?,
        capturedBefore: Instant?
    ): ApiPage<ApiMeasurement> {
        if (limit !in 1..100) throw invalidResponse()
        if (capturedFrom != null && capturedBefore != null && !capturedFrom.isBefore(capturedBefore)) {
            throw invalidResponse()
        }

        val query = buildList {
            add("limit=$limit")
            cursor?.let { add("cursor=${encodeQuery(it)}") }
            capturedFrom?.let { add("captured_from=${encodeQuery(it.toString())}") }
            capturedBefore?.let { add("captured_before=${encodeQuery(it.toString())}") }
        }.joinToString("&")

        val envelope = request(contextProvider(), "GET", "telemetry/measurements?$query")
        val data = envelope.requireDataArray()
        val meta = envelope.requireMeta()
        return ApiPage(
            items = data.objects().map { it.toMeasurement() },
            hasMore = meta.requireBoolean("has_more"),
            nextCursor = meta.nullableString("next_cursor")
        ).also { page ->
            if (page.hasMore && page.nextCursor.isNullOrBlank()) throw invalidResponse()
            if (!page.hasMore && page.nextCursor != null) throw invalidResponse()
        }
    }

    override suspend fun commands(limit: Int, cursor: String?): ApiPage<ApiCommand> {
        if (limit !in 1..100) throw invalidResponse()
        val query = buildList {
            add("limit=$limit")
            cursor?.let { add("cursor=${encodeQuery(it)}") }
        }.joinToString("&")
        val envelope = request(contextProvider(), "GET", "commands?$query")
        return envelope.page { it.toCommand() }
    }

    override suspend fun command(commandUuid: String): ApiCommand =
        request(
            contextProvider(),
            "GET",
            "commands/${requireUuid(commandUuid)}"
        ).requireDataObject().toCommand()

    override suspend fun createSetStateCommand(
        actuatorKey: String,
        targetState: Boolean
    ): ApiCommand {
        if (actuatorKey !in HydroApiContract.actuatorKeys) throw invalidResponse()
        val commandUuid = uuid().toString()
        val context = contextProvider()
        val created = requestIdempotent(
            context = context,
            method = "POST",
            path = "commands",
            body = JSONObject().apply {
                put("command_uuid", commandUuid)
                put("actuator_key", actuatorKey)
                put("command_key", "set_state")
                put("target_state", if (targetState) 1 else 0)
                put("expires_at", now().plusSeconds(INTENT_TTL_SECONDS).toString())
            },
            idempotencyKey = commandUuid
        ).requireDataObject().toCommand()
        invalidateCachedReads(setOf("commands", "catalogs/actuators"))
        return created
    }

    override suspend fun dosingRequest(requestUuid: String): ApiDosingRequest =
        request(
            contextProvider(),
            "GET",
            "dosing-requests/${requireUuid(requestUuid)}"
        ).requireDataObject().toDosingRequest()

    override suspend fun createDosingRequest(
        nutrientKey: String,
        amountMl: Double
    ): ApiDosingRequest {
        if (nutrientKey !in HydroApiContract.nutrientKeys || !amountMl.isFinite() ||
            amountMl <= 0.0 || amountMl > MAX_DOSING_ML
        ) {
            throw invalidResponse()
        }
        val requestUuid = uuid().toString()
        val context = contextProvider()
        val created = requestIdempotent(
            context = context,
            method = "POST",
            path = "dosing-requests",
            body = JSONObject().apply {
                put("request_uuid", requestUuid)
                put("nutrient_key", nutrientKey)
                put("amount_ml", amountMl)
                put("expires_at", now().plusSeconds(INTENT_TTL_SECONDS).toString())
            },
            idempotencyKey = requestUuid
        ).requireDataObject().toDosingRequest()
        invalidateCachedReads(setOf("dosing-requests", "commands", "catalogs/actuators"))
        return created
    }

    private suspend fun <T> list(path: String, transform: (JSONObject) -> T): List<T> {
        val envelope = request(contextProvider(), "GET", path)
        return envelope.requireDataArray().objects().map(transform)
    }

    private suspend fun activeCycle(
        context: DomainApiContext,
        allowCache: Boolean
    ): ApiCycle? {
        val envelope = request(
            context,
            "GET",
            "cycles?active=true&limit=2",
            allowCache = allowCache
        )
        val cycles = envelope.requireDataArray().objects().map { it.toCycle() }
        if (cycles.size > 1) throw invalidResponse()
        return cycles.singleOrNull()
    }

    private suspend fun requestIdempotent(
        context: DomainApiContext,
        method: String,
        path: String,
        body: JSONObject,
        idempotencyKey: String
    ): JSONObject {
        repeat(2) { attempt ->
            try {
                return request(context, method, path, body, idempotencyKey)
            } catch (error: DomainApiException) {
                if (!error.problem.transient || attempt == 1) throw error
                retryDelay(MUTATION_RETRY_DELAY_MILLIS)
            }
        }
        throw invalidResponse()
    }

    private suspend fun request(
        context: DomainApiContext,
        method: String,
        path: String,
        body: JSONObject? = null,
        idempotencyKey: String? = null,
        allowCache: Boolean = method == "GET"
    ): JSONObject {
        validateContext(context)
        val attempts = if (method == "GET") READ_ATTEMPTS else 1
        var lastTransient: DomainApiException? = null

        repeat(attempts) { attempt ->
            try {
                val raw = executeNetworkRequest(context, method, path, body, idempotencyKey)
                val envelope = parseEnvelope(raw)
                markAuthenticatedSuccess()
                mutableDataStatus.value = DomainDataStatus(
                    source = DomainDataSource.LIVE,
                    observedAt = now()
                )
                if (method == "GET" && allowCache) cacheResponse(path, raw)
                return envelope
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: DomainApiException) {
                if (!error.problem.transient) {
                    mutableDataStatus.value = DomainDataStatus(
                        source = DomainDataSource.UNAVAILABLE,
                        observedAt = now(),
                        problemCode = error.problem.code
                    )
                    throw error
                }
                lastTransient = error
                if (attempt + 1 < attempts) retryDelay(READ_RETRY_DELAY_MILLIS)
            }
        }

        val failure = lastTransient ?: invalidResponse()
        if (method == "GET" && allowCache) {
            cachedEnvelope(path, failure.problem.code)?.let { return it }
        }
        mutableDataStatus.value = DomainDataStatus(
            source = DomainDataSource.UNAVAILABLE,
            observedAt = now(),
            problemCode = failure.problem.code
        )
        throw failure
    }

    private suspend fun executeNetworkRequest(
        context: DomainApiContext,
        method: String,
        path: String,
        body: JSONObject?,
        idempotencyKey: String?
    ): String = withContext(Dispatchers.IO) {
        val connection = try {
            connectionFactory(URL("${baseUrl.trimEnd('/')}/sites/${context.siteKey}/${path.trimStart('/')}"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw networkFailure(error)
        }

        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.doInput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${context.accessToken}")
            idempotencyKey?.let { connection.setRequestProperty("Idempotency-Key", it) }

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                    it.write(body.toString())
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (status !in 200..299) {
                if (status == 401) invalidateSessionBestEffort()
                throw problem(status, text)
            }
            text
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: DomainApiException) {
            throw known
        } catch (error: IOException) {
            throw networkFailure(error)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun cacheResponse(path: String, raw: String) {
        val scope = cacheScopeProvider()?.takeIf(String::isNotBlank) ?: return
        try {
            responseCache.write(scope, path, CachedDomainResponse(raw, now()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A cache write must never turn a valid API response into an error.
        }
    }

    private suspend fun cachedEnvelope(path: String, problemCode: String): JSONObject? {
        val scope = cacheScopeProvider()?.takeIf(String::isNotBlank) ?: return null
        val cached = try {
            responseCache.read(scope, path)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return null

        val observedAt = now()
        if (cached.storedAt.isAfter(observedAt.plus(FUTURE_CACHE_TOLERANCE))) {
            removeCachedResponse(scope, path)
            return null
        }
        val age = if (cached.storedAt.isAfter(observedAt)) {
            Duration.ZERO
        } else {
            Duration.between(cached.storedAt, observedAt)
        }
        val policy = DomainCachePolicies.forPath(path)
        if (age > policy.maximumOfflineAge) {
            removeCachedResponse(scope, path)
            return null
        }

        val envelope = try {
            parseEnvelope(cached.body)
        } catch (_: DomainApiException) {
            removeCachedResponse(scope, path)
            return null
        }
        mutableDataStatus.value = DomainDataStatus(
            source = if (age <= policy.freshFor) {
                DomainDataSource.CACHE_FRESH
            } else {
                DomainDataSource.CACHE_STALE
            },
            observedAt = observedAt,
            cacheStoredAt = cached.storedAt,
            problemCode = problemCode
        )
        return envelope
    }

    private suspend fun removeCachedResponse(scope: String, path: String) {
        try {
            responseCache.remove(scope, path)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Corrupt/expired cache is already treated as unavailable.
        }
    }

    private suspend fun invalidateCachedReads(prefixes: Set<String>) {
        val scope = cacheScopeProvider()?.takeIf(String::isNotBlank) ?: return
        try {
            responseCache.removeByPrefix(scope, prefixes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A failed invalidation cannot rewrite or queue the accepted mutation.
        }
    }

    private suspend fun markAuthenticatedSuccess() {
        try {
            onAuthenticatedSuccess()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Session metadata is advisory after the server accepted this request.
        }
    }

    private fun parseEnvelope(raw: String): JSONObject = runCatching {
        JSONObject(raw).also { it.requireMeta() }
    }.getOrElse { error ->
        if (error is DomainApiException) throw error
        throw invalidResponse()
    }

    private fun problem(status: Int, raw: String): DomainApiException {
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val code = json?.optString("code")?.takeIf(String::isNotBlank) ?: "http.$status"
        val detail = json?.optString("detail")?.takeIf(String::isNotBlank)
        val requestId = json?.optString("request_id")?.takeIf(String::isNotBlank)
        return DomainApiException(
            ApiProblem(status, code, detail, requestId, transient = status == 429 || status >= 500)
        )
    }

    private fun JSONObject.requireMeta(): JSONObject = getJSONObject("meta").also { meta ->
        if (meta.requireString("api_version") != API_VERSION) throw invalidResponse()
        requireUuid(meta.requireString("request_id"))
    }

    private fun JSONObject.requireDataArray(): JSONArray {
        requireMeta()
        return getJSONArray("data")
    }

    private fun JSONObject.requireDataObject(): JSONObject {
        requireMeta()
        return getJSONObject("data")
    }

    private fun JSONObject.toCycle(): ApiCycle = ApiCycle(
        cycleUuid = requireUuid(requireString("cycle_uuid")),
        cropKey = requireAllowedKey("crop_key", HydroApiContract.cropKeys),
        startedAt = nullableInstant("started_at"),
        endedAt = nullableInstant("ended_at"),
        plannedDurationDays = nullablePositiveInt("planned_duration_days"),
        active = requireBoolean("is_active"),
        source = requireString("source"),
        createdAt = requireInstant("created_at")
    )

    private fun JSONObject.toMeasurement(): ApiMeasurement {
        val readingsObject = getJSONObject("readings")
        val readings = readingsObject.keys().asSequence().associateWith { key ->
            if (key !in HydroApiContract.sensorKeys) throw invalidResponse()
            readingsObject.requireFiniteNumber(key)
        }
        if (readings.isEmpty()) throw invalidResponse()

        return ApiMeasurement(
            eventUuid = requireUuid(requireString("event_uuid")),
            deviceKey = requireLogicalKey("device_key"),
            capturedAt = requireInstant("captured_at"),
            ingestedAt = requireInstant("ingested_at"),
            cropKey = nullableString("crop_key")?.also(::requireCropKey),
            cycleUuid = nullableString("cycle_uuid")?.let(::requireUuid),
            readings = readings
        )
    }

    private fun JSONObject.toCommand(): ApiCommand {
        val commandKey = requireAllowedKey("command_key", HydroApiContract.commandKeys)
        val targetState = nullableBinaryState("target_state")
        val durationMs = nullablePositiveLong("duration_ms")
        if ((commandKey == "set_state") != (targetState != null) ||
            (commandKey == "run_for") != (durationMs != null)
        ) throw invalidResponse()

        val evidence = getJSONObject("physical_evidence")
        return ApiCommand(
            commandUuid = requireUuid(requireString("command_uuid")),
            actuatorKey = requireAllowedKey("actuator_key", HydroApiContract.actuatorKeys),
            commandKey = commandKey,
            targetState = targetState,
            durationMs = durationMs,
            statusKey = requireAllowedKey("status_key", HydroApiContract.commandStatusKeys),
            requestedAt = requireInstant("requested_at"),
            sentAt = nullableInstant("sent_at"),
            acknowledgedAt = nullableInstant("acknowledged_at"),
            failedAt = nullableInstant("failed_at"),
            expiresAt = requireInstant("expires_at"),
            errorCode = nullableString("error_code"),
            errorMessage = nullableString("error_message"),
            physicalEvidence = ApiPhysicalEvidence(
                acceptedAt = evidence.nullableInstant("accepted_at"),
                completedAt = evidence.nullableInstant("completed_at"),
                completionBasis = evidence.nullableString("completion_basis")?.also {
                    if (it !in HydroApiContract.completionBases) throw invalidResponse()
                },
                reportedState = evidence.nullableBinaryState("reported_state")
            )
        )
    }

    private fun JSONObject.toDosingRequest(): ApiDosingRequest = ApiDosingRequest(
        requestUuid = requireUuid(requireString("request_uuid")),
        nutrientKey = requireAllowedKey("nutrient_key", HydroApiContract.nutrientKeys),
        actuatorKey = requireAllowedKey("actuator_key", HydroApiContract.actuatorKeys),
        amountMl = requireFiniteNumber("amount_ml").takeIf { it > 0.0 } ?: throw invalidResponse(),
        statusKey = requireAllowedKey("status_key", HydroApiContract.dosingStatusKeys),
        commandUuid = nullableString("command_uuid")?.let(::requireUuid),
        applicationUuid = nullableString("application_uuid")?.let(::requireUuid),
        requestedAt = requireInstant("requested_at"),
        expiresAt = requireInstant("expires_at"),
        correlatedAt = nullableInstant("correlated_at"),
        completedAt = nullableInstant("completed_at"),
        failedAt = nullableInstant("failed_at"),
        errorMessage = nullableString("error_message"),
        updatedAt = requireInstant("updated_at")
    )

    private fun <T> JSONObject.page(transform: (JSONObject) -> T): ApiPage<T> {
        val data = requireDataArray()
        val meta = requireMeta()
        return ApiPage(
            items = data.objects().map(transform),
            hasMore = meta.requireBoolean("has_more"),
            nextCursor = meta.nullableString("next_cursor")
        ).also { page ->
            if (page.hasMore && page.nextCursor.isNullOrBlank()) throw invalidResponse()
            if (!page.hasMore && page.nextCursor != null) throw invalidResponse()
        }
    }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).map { index -> getJSONObject(index) }

    private fun JSONObject.requireString(key: String): String =
        getString(key).takeIf(String::isNotBlank) ?: throw invalidResponse()

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else requireString(key)

    private fun JSONObject.requireBoolean(key: String): Boolean {
        val value = get(key)
        if (value !is Boolean) throw invalidResponse()
        return value
    }

    private fun JSONObject.requirePositiveInt(key: String): Int =
        getInt(key).takeIf { it > 0 } ?: throw invalidResponse()

    private fun JSONObject.nullablePositiveInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else requirePositiveInt(key)

    private fun JSONObject.nullablePositiveLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key).takeIf { it > 0L } ?: throw invalidResponse()

    private fun JSONObject.requireBinaryState(key: String): Boolean = when (getInt(key)) {
        0 -> false
        1 -> true
        else -> throw invalidResponse()
    }

    private fun JSONObject.nullableBinaryState(key: String): Boolean? =
        if (!has(key) || isNull(key)) null else requireBinaryState(key)

    private fun JSONObject.requireFiniteNumber(key: String): Double =
        getDouble(key).takeIf(Double::isFinite) ?: throw invalidResponse()

    private fun JSONObject.requireAllowedKey(key: String, allowed: Set<String>): String =
        requireString(key).takeIf(allowed::contains) ?: throw invalidResponse()

    private fun JSONObject.requireLogicalKey(key: String): String =
        requireString(key).takeIf(LOGICAL_KEY::matches) ?: throw invalidResponse()

    private fun JSONObject.requireInstant(key: String): Instant =
        parseUtcInstant(requireString(key))

    private fun JSONObject.nullableInstant(key: String): Instant? =
        nullableString(key)?.let(::parseUtcInstant)

    private fun parseUtcInstant(value: String): Instant {
        if (!value.endsWith("Z")) throw invalidResponse()
        return runCatching { Instant.parse(value) }.getOrElse { throw invalidResponse() }
    }

    private fun requireUuid(value: String): String =
        runCatching { UUID.fromString(value).toString() }
            .getOrElse { throw invalidResponse() }
            .also { if (!it.equals(value, ignoreCase = true)) throw invalidResponse() }

    private fun requireCropKey(value: String) {
        if (value !in HydroApiContract.cropKeys) throw invalidResponse()
    }

    private fun validateContext(context: DomainApiContext) {
        if (context.accessToken.isBlank() || !LOGICAL_KEY.matches(context.siteKey)) {
            throw DomainApiException(ApiProblem(401, "auth.session_required"))
        }
    }

    private fun encodeQuery(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun invalidResponse(): DomainApiException =
        DomainApiException(ApiProblem(502, "api.invalid_response"))

    private fun networkFailure(cause: Exception): DomainApiException =
        DomainApiException(ApiProblem(null, "network.unavailable", transient = true)).also {
            it.initCause(cause)
        }

    private suspend fun invalidateSessionBestEffort() {
        try {
            onUnauthorized()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The original 401 remains the canonical domain error. SessionManager
            // still clears its in-memory state even if secure storage is unavailable.
        }
    }

    companion object {
        private const val API_VERSION = "1"
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val READ_TIMEOUT_MILLIS = 15_000
        private const val READ_ATTEMPTS = 2
        private const val READ_RETRY_DELAY_MILLIS = 250L
        private const val MUTATION_RETRY_DELAY_MILLIS = 250L
        private const val INTENT_TTL_SECONDS = 5L * 60L
        private const val MAX_DOSING_ML = 9_999_999.999
        private val FUTURE_CACHE_TOLERANCE = Duration.ofMinutes(5)
        // Keep this aligned with API v1's LogicalKey schema in openapi.json.
        private val LOGICAL_KEY = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")
    }
}

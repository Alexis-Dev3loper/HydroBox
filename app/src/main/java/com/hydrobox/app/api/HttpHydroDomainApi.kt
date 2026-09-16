package com.hydrobox.app.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

class HttpHydroDomainApi(
    private val baseUrl: String,
    private val contextProvider: suspend () -> DomainApiContext,
    private val onUnauthorized: suspend () -> Unit = {},
    private val now: () -> Instant = Instant::now,
    private val uuid: () -> UUID = UUID::randomUUID,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    }
) : HydroDomainApi {

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
        return activeCycle(context)
    }

    override suspend fun changeActiveCrop(
        cropKey: String,
        plannedDurationDays: Int?
    ): ApiCycle {
        requireCropKey(cropKey)
        if (plannedDurationDays != null && plannedDurationDays < 1) throw invalidResponse()

        val context = contextProvider()
        val current = activeCycle(context)
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
        }

        val cycleUuid = uuid().toString()
        return request(
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
            items = data.objects().map(JSONObject::toMeasurement),
            hasMore = meta.requireBoolean("has_more"),
            nextCursor = meta.nullableString("next_cursor")
        ).also { page ->
            if (page.hasMore && page.nextCursor.isNullOrBlank()) throw invalidResponse()
            if (!page.hasMore && page.nextCursor != null) throw invalidResponse()
        }
    }

    private suspend fun <T> list(path: String, transform: (JSONObject) -> T): List<T> {
        val envelope = request(contextProvider(), "GET", path)
        return envelope.requireDataArray().objects().map(transform)
    }

    private suspend fun activeCycle(context: DomainApiContext): ApiCycle? {
        val envelope = request(context, "GET", "cycles?active=true&limit=2")
        val cycles = envelope.requireDataArray().objects().map(JSONObject::toCycle)
        if (cycles.size > 1) throw invalidResponse()
        return cycles.singleOrNull()
    }

    private suspend fun request(
        context: DomainApiContext,
        method: String,
        path: String,
        body: JSONObject? = null,
        idempotencyKey: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        validateContext(context)
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
            parseEnvelope(text)
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
        return DomainApiException(ApiProblem(status, code, detail, requestId))
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
        private val LOGICAL_KEY = Regex("^[a-z][a-z0-9_]{1,49}$")
    }
}

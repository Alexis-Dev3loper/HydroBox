package com.hydrobox.app.api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

class HttpHydroDomainApiTest {
    private val context = DomainApiContext("access-token", "university-lab")

    @Test
    fun readsCanonicalCatalogsWithBearerAndLogicalKeys() = runBlocking {
        val connections = mutableListOf<StubHttpURLConnection>()
        val responses = ArrayDeque(
            listOf(
                StubResponse(200, envelope("""[{"sensor_key":"ph","name":"pH","unit_symbol":"pH","description":null,"display_order":1,"is_active":true}]""")),
                StubResponse(200, envelope("""[{"crop_key":"chard","name":"Acelga","description":null,"default_cycle_days":35,"is_active":true}]""")),
                StubResponse(200, envelope("""[{"sensor_key":"ph","unit_symbol":"pH","min_value":5.5,"max_value":6.5}]"""))
            )
        )
        val api = api(responses, connections)

        val sensors = api.sensors()
        val crops = api.crops()
        val ranges = api.sensorRanges("chard")

        assertEquals("ph", sensors.single().sensorKey)
        assertEquals("chard", crops.single().cropKey)
        assertEquals(35, crops.single().defaultCycleDays)
        assertEquals(5.5, ranges.single().minValue, 0.0)
        assertEquals(
            "https://api.example.test/api/v1/sites/university-lab/catalogs/sensors",
            connections.first().url.toString()
        )
        assertEquals("Bearer access-token", connections.first().headers["Authorization"])
    }

    @Test
    fun readsActuatorsAndNutrientsWithoutCollapsingReportedState() = runBlocking {
        val responses = ArrayDeque(
            listOf(
                StubResponse(200, envelope("""[{"actuator_key":"fan","name":"Ventilador","actuator_type":"switch","safe_state":0,"is_active":true,"state":{"desired_state":1,"reported_state":null,"availability_key":"unknown","desired_changed_at":"2026-09-15T12:00:00Z","reported_at":null,"last_seen_at":null}}]""")),
                StubResponse(200, envelope("""[{"nutrient_key":"flora_grow","name":"Flora Grow","dosing_actuator_key":"flora_grow_pump","description":null,"is_active":true}]"""))
            )
        )
        val api = api(responses, mutableListOf())

        val actuator = api.actuators().single()
        val nutrient = api.nutrients().single()

        assertTrue(actuator.state.desiredState)
        assertNull(actuator.state.reportedState)
        assertEquals("unknown", actuator.state.availabilityKey)
        assertEquals("flora_grow_pump", nutrient.dosingActuatorKey)
    }

    @Test
    fun createsListsAndReadsCommandsWithOneIdempotentUuid() = runBlocking {
        val commandUuid = UUID.fromString("44444444-4444-4444-8444-444444444444")
        val pending = commandJson(commandUuid.toString(), "pending", null, null)
        val sent = commandJson(commandUuid.toString(), "sent", null, null)
        val acknowledged = commandJson(
            commandUuid.toString(),
            "acknowledged",
            "2026-09-15T12:00:03Z",
            1
        )
        val responses = ArrayDeque(
            listOf(
                StubResponse(202, envelope(pending)),
                StubResponse(200, pageEnvelope("[$sent]", false, null)),
                StubResponse(200, envelope(acknowledged))
            )
        )
        val connections = mutableListOf<StubHttpURLConnection>()
        val api = api(responses, connections, uuid = { commandUuid })

        val created = api.createSetStateCommand("fan", true)
        val listed = api.commands().items.single()
        val confirmed = api.command(commandUuid.toString())

        assertEquals("pending", created.statusKey)
        assertEquals("sent", listed.statusKey)
        assertEquals("acknowledged", confirmed.statusKey)
        assertTrue(confirmed.physicalEvidence.reportedState == true)
        assertEquals(commandUuid.toString(), connections.first().headers["Idempotency-Key"])
        assertTrue(connections.first().requestBodyText.contains("\"target_state\":1"))
        assertTrue(connections.first().requestBodyText.contains("\"expires_at\":\"2026-09-15T12:05:00Z\""))
        assertEquals(listOf("POST", "GET", "GET"), connections.map { it.requestMethod })
    }

    @Test
    fun createsAndReadsDurableDosingWithoutInventingPhysicalCommand() = runBlocking {
        val requestUuid = UUID.fromString("55555555-5555-4555-8555-555555555555")
        val pending = dosingJson(requestUuid.toString(), "pending", null, null)
        val completed = dosingJson(
            requestUuid.toString(),
            "completed",
            "66666666-6666-4666-8666-666666666666",
            "77777777-7777-4777-8777-777777777777"
        )
        val responses = ArrayDeque(
            listOf(
                StubResponse(202, envelope(pending)),
                StubResponse(200, envelope(completed))
            )
        )
        val connections = mutableListOf<StubHttpURLConnection>()
        val api = api(responses, connections, uuid = { requestUuid })

        val created = api.createDosingRequest("flora_grow", 12.5)
        val confirmed = api.dosingRequest(requestUuid.toString())

        assertEquals("pending", created.statusKey)
        assertNull(created.commandUuid)
        assertNull(created.applicationUuid)
        assertEquals("completed", confirmed.statusKey)
        assertEquals(requestUuid.toString(), connections.first().headers["Idempotency-Key"])
        assertTrue(connections.first().requestBodyText.contains("\"amount_ml\":12.5"))
        assertFalse(connections.first().requestBodyText.contains("duration_ms"))
    }

    @Test
    fun retriesLostIdempotentCommandWithTheSameUuidAndPayload() = runBlocking {
        val commandUuid = UUID.fromString("88888888-8888-4888-8888-888888888888")
        val responses = ArrayDeque(
            listOf(
                StubResponse(0, "", IOException("response lost")),
                StubResponse(202, envelope(commandJson(commandUuid.toString(), "pending", null, null)))
            )
        )
        val connections = mutableListOf<StubHttpURLConnection>()
        val api = api(responses, connections, uuid = { commandUuid })

        val command = api.createSetStateCommand("fan", true)

        assertEquals("pending", command.statusKey)
        assertEquals(2, connections.size)
        assertEquals(
            connections[0].headers["Idempotency-Key"],
            connections[1].headers["Idempotency-Key"]
        )
        assertEquals(connections[0].requestBodyText, connections[1].requestBodyText)
    }

    @Test
    fun rejectsContradictoryCommandShapesAndUnknownLifecycle() = runBlocking {
        val invalid = listOf(
            commandJson("44444444-4444-4444-8444-444444444444", "unknown", null, null),
            commandJson("44444444-4444-4444-8444-444444444444", "pending", null, null)
                .replace("\"duration_ms\":null", "\"duration_ms\":100")
        )

        invalid.forEach { body ->
            val error = expectDomainError {
                api(ArrayDeque(listOf(StubResponse(200, envelope(body)))), mutableListOf())
                    .command("44444444-4444-4444-8444-444444444444")
            }
            assertEquals("api.invalid_response", error.problem.code)
        }
    }

    @Test
    fun changesCropByClosingCurrentCycleAndCreatingAnIdempotentUuidIntent() = runBlocking {
        val current = cycleJson(
            uuid = "11111111-1111-4111-8111-111111111111",
            cropKey = "lettuce",
            active = true,
            endedAt = null
        )
        val closed = cycleJson(
            uuid = "11111111-1111-4111-8111-111111111111",
            cropKey = "lettuce",
            active = false,
            endedAt = "2026-09-15T12:00:00Z"
        )
        val createdUuid = UUID.fromString("22222222-2222-4222-8222-222222222222")
        val created = cycleJson(createdUuid.toString(), "chard", true, null)
        val responses = ArrayDeque(
            listOf(
                StubResponse(200, pageEnvelope("[$current]", false, null)),
                StubResponse(200, envelope(closed)),
                StubResponse(201, envelope(created))
            )
        )
        val connections = mutableListOf<StubHttpURLConnection>()
        val api = api(
            responses,
            connections,
            now = { Instant.parse("2026-09-15T12:00:00Z") },
            uuid = { createdUuid }
        )

        val result = api.changeActiveCrop("chard", 35)

        assertEquals("chard", result.cropKey)
        assertEquals(listOf("GET", "PATCH", "POST"), connections.map { it.requestMethod })
        assertTrue(connections[1].requestBodyText.contains("\"is_active\":false"))
        assertTrue(connections[1].requestBodyText.contains("\"ended_at\":\"2026-09-15T12:00:00Z\""))
        assertEquals(createdUuid.toString(), connections[2].headers["Idempotency-Key"])
        assertTrue(connections[2].requestBodyText.contains("\"crop_key\":\"chard\""))
        assertFalse(connections[2].requestBodyText.contains("crop_id"))
    }

    @Test
    fun selectingTheAlreadyActiveCropDoesNotMutateIt() = runBlocking {
        val current = cycleJson(
            uuid = "11111111-1111-4111-8111-111111111111",
            cropKey = "lettuce",
            active = true,
            endedAt = null
        )
        val responses = ArrayDeque(listOf(StubResponse(200, pageEnvelope("[$current]", false, null))))
        val connections = mutableListOf<StubHttpURLConnection>()

        val result = api(responses, connections).changeActiveCrop("lettuce", 45)

        assertEquals("lettuce", result.cropKey)
        assertEquals(listOf("GET"), connections.map { it.requestMethod })
    }

    @Test
    fun preservesZeroMissingReadingsUtcAndOpaquePagination() = runBlocking {
        val responses = ArrayDeque(
            listOf(
                StubResponse(
                    200,
                    pageEnvelope(
                        """[{"event_uuid":"33333333-3333-4333-8333-333333333333","device_key":"edge_primary","source_record_key":null,"captured_at":"2026-09-15T11:59:59Z","ingested_at":"2026-09-15T12:00:00Z","crop_key":"lettuce","cycle_uuid":"11111111-1111-4111-8111-111111111111","readings":{"ph":0}}]""",
                        true,
                        "opaque+/cursor="
                    )
                )
            )
        )
        val connections = mutableListOf<StubHttpURLConnection>()
        val page = api(responses, connections).measurements(
            limit = 1,
            cursor = "previous+/cursor=",
            capturedFrom = Instant.parse("2026-09-15T00:00:00Z"),
            capturedBefore = Instant.parse("2026-09-16T00:00:00Z")
        )

        assertEquals(0.0, page.items.single().readings.getValue("ph"), 0.0)
        assertFalse(page.items.single().readings.containsKey("orp"))
        assertEquals(Instant.parse("2026-09-15T11:59:59Z"), page.items.single().capturedAt)
        assertTrue(page.hasMore)
        assertEquals("opaque+/cursor=", page.nextCursor)
        assertTrue(connections.single().url.query.contains("cursor=previous%2B%2Fcursor%3D"))
    }

    @Test
    fun rejectsUnknownApiVersionAndUnknownReadingKey() = runBlocking {
        val cases = listOf(
            """{"data":[],"meta":{"api_version":"2","request_id":"$REQUEST_ID","has_more":false,"next_cursor":null}}""",
            pageEnvelope(
                """[{"event_uuid":"33333333-3333-4333-8333-333333333333","device_key":"edge_primary","source_record_key":null,"captured_at":"2026-09-15T11:59:59Z","ingested_at":"2026-09-15T12:00:00Z","crop_key":null,"cycle_uuid":null,"readings":{"legacy_ce":1.2}}]""",
                false,
                null
            )
        )

        cases.forEach { body ->
            val error = expectDomainError {
                api(ArrayDeque(listOf(StubResponse(200, body))), mutableListOf())
                    .measurements()
            }
            assertEquals("api.invalid_response", error.problem.code)
        }
    }

    @Test
    fun parsesProblemDetailsAndInvalidatesOnlyUnauthorizedSessions() = runBlocking {
        var unauthorizedCalls = 0
        val responses = ArrayDeque(
            listOf(
                StubResponse(401, problem(401, "auth.token_revoked")),
                StubResponse(403, problem(403, "authorization.scope_denied"))
            )
        )
        val api = api(responses, mutableListOf(), onUnauthorized = { unauthorizedCalls += 1 })

        val unauthorized = expectDomainError { api.sensors() }
        val forbidden = expectDomainError { api.sensors() }

        assertEquals("auth.token_revoked", unauthorized.problem.code)
        assertEquals(REQUEST_ID, unauthorized.problem.requestId)
        assertEquals("authorization.scope_denied", forbidden.problem.code)
        assertEquals(1, unauthorizedCalls)
        assertFalse(unauthorized.message.orEmpty().contains("Access denied"))
    }

    @Test
    fun classifiesOfflineWithoutLeakingTransportDetails() = runBlocking {
        val api = HttpHydroDomainApi(
            baseUrl = BASE_URL,
            contextProvider = { context },
            connectionFactory = { throw IOException("private-host.example: secret detail") }
        )

        val error = expectDomainError { api.sensors() }

        assertEquals("network.unavailable", error.problem.code)
        assertTrue(error.problem.transient)
        assertFalse(error.message.orEmpty().contains("private-host"))
    }

    @Test
    fun contextResolverFailsClosedForMissingOrAmbiguousSites() {
        val missing = runCatching { DomainApiContextResolver.resolve("token", emptyList()) }.exceptionOrNull()
        val ambiguous = runCatching {
            DomainApiContextResolver.resolve("token", listOf("site-a", "site-b"))
        }.exceptionOrNull()

        assertEquals("authorization.site_required", (missing as DomainApiException).problem.code)
        assertEquals("mobile.site_selection_required", (ambiguous as DomainApiException).problem.code)
        assertEquals("site-a", DomainApiContextResolver.resolve("token", listOf("site-a", "site-a")).siteKey)
    }

    private fun api(
        responses: ArrayDeque<StubResponse>,
        connections: MutableList<StubHttpURLConnection>,
        now: () -> Instant = { Instant.parse("2026-09-15T12:00:00Z") },
        uuid: () -> UUID = { UUID.randomUUID() },
        onUnauthorized: suspend () -> Unit = {}
    ) = HttpHydroDomainApi(
        baseUrl = BASE_URL,
        contextProvider = { context },
        onUnauthorized = onUnauthorized,
        now = now,
        uuid = uuid,
        connectionFactory = { url ->
            val response = responses.removeFirst()
            StubHttpURLConnection(
                url,
                response.status,
                response.body,
                response.responseFailure
            ).also(connections::add)
        }
    )

    private suspend fun expectDomainError(block: suspend () -> Unit): DomainApiException {
        try {
            block()
            fail("Expected DomainApiException")
        } catch (error: DomainApiException) {
            return error
        }
        throw AssertionError("unreachable")
    }

    private fun envelope(data: String): String =
        """{"data":$data,"meta":{"api_version":"1","request_id":"$REQUEST_ID"}}"""

    private fun pageEnvelope(data: String, hasMore: Boolean, nextCursor: String?): String =
        """{"data":$data,"meta":{"api_version":"1","request_id":"$REQUEST_ID","has_more":$hasMore,"next_cursor":${nextCursor?.let { "\"$it\"" } ?: "null"}}}"""

    private fun problem(status: Int, code: String): String =
        """{"type":"about:blank","title":"Request failed","status":$status,"code":"$code","detail":"Access denied","request_id":"$REQUEST_ID"}"""

    private fun cycleJson(uuid: String, cropKey: String, active: Boolean, endedAt: String?): String =
        """{"cycle_uuid":"$uuid","crop_key":"$cropKey","started_at":"2026-09-01T00:00:00Z","ended_at":${endedAt?.let { "\"$it\"" } ?: "null"},"planned_duration_days":45,"is_active":$active,"source":"api_v1","notes":null,"created_at":"2026-09-01T00:00:00Z","updated_at":null}"""

    private fun commandJson(
        uuid: String,
        status: String,
        completedAt: String?,
        reportedState: Int?
    ): String =
        """{"command_uuid":"$uuid","actuator_key":"fan","command_key":"set_state","target_state":1,"duration_ms":null,"status_key":"$status","requested_at":"2026-09-15T12:00:00Z","sent_at":${if (status == "pending") "null" else "\"2026-09-15T12:00:01Z\""},"acknowledged_at":${completedAt?.let { "\"$it\"" } ?: "null"},"failed_at":null,"expires_at":"2026-09-15T12:05:00Z","error_code":null,"error_message":null,"physical_evidence":{"accepted_at":null,"completed_at":${completedAt?.let { "\"$it\"" } ?: "null"},"completion_basis":${completedAt?.let { "\"output_applied\"" } ?: "null"},"reported_state":${reportedState ?: "null"}}}"""

    private fun dosingJson(
        uuid: String,
        status: String,
        commandUuid: String?,
        applicationUuid: String?
    ): String =
        """{"request_uuid":"$uuid","nutrient_key":"flora_grow","actuator_key":"flora_grow_pump","amount_ml":12.5,"status_key":"$status","command_uuid":${commandUuid?.let { "\"$it\"" } ?: "null"},"application_uuid":${applicationUuid?.let { "\"$it\"" } ?: "null"},"requested_at":"2026-09-15T12:00:00Z","expires_at":"2026-09-15T12:05:00Z","correlated_at":${commandUuid?.let { "\"2026-09-15T12:00:01Z\"" } ?: "null"},"completed_at":${applicationUuid?.let { "\"2026-09-15T12:00:03Z\"" } ?: "null"},"failed_at":null,"error_message":null,"updated_at":"2026-09-15T12:00:03Z"}"""

    private data class StubResponse(
        val status: Int,
        val body: String,
        val responseFailure: IOException? = null
    )

    private class StubHttpURLConnection(
        url: URL,
        private val status: Int,
        private val responseBody: String,
        private val responseFailure: IOException? = null
    ) : HttpURLConnection(url) {
        private val requestBody = ByteArrayOutputStream()
        private var recordedRequestMethod = "GET"
        val headers = mutableMapOf<String, String>()
        val requestBodyText: String get() = requestBody.toString(Charsets.UTF_8.name())

        override fun setRequestMethod(method: String) {
            recordedRequestMethod = method
        }

        override fun getRequestMethod(): String = recordedRequestMethod

        override fun setRequestProperty(key: String, value: String) {
            headers[key] = value
        }

        override fun getOutputStream() = requestBody
        override fun getResponseCode(): Int = responseFailure?.let { throw it } ?: status
        override fun getInputStream() = ByteArrayInputStream(responseBody.toByteArray())
        override fun getErrorStream() = ByteArrayInputStream(responseBody.toByteArray())
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }

    companion object {
        private const val BASE_URL = "https://api.example.test/api/v1"
        private const val REQUEST_ID = "99999999-9999-4999-8999-999999999999"
    }
}

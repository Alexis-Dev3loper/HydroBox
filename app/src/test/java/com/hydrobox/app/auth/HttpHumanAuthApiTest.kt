package com.hydrobox.app.auth

import com.hydrobox.app.auth.session.AuthApiException
import com.hydrobox.app.auth.session.HttpHumanAuthApi
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class HttpHumanAuthApiTest {
    @Test
    fun clientMatchesTheVersionedTokenProfileRefreshAndLogoutContract() = runBlocking {
        val responses = ArrayDeque(
            listOf(
                StubResponse(200, tokenEnvelope("access-1", "refresh-1")),
                StubResponse(
                    200,
                    """
                    {"data":{"principal_uuid":"a57d5516-fcf8-4bfe-8c89-c480c11e2bdb","principal_type":"user","name":"Hydro Operator","email":"operator@example.test","role_key":"operator","status_key":"active","site_keys":["university-lab"],"scopes":["profile:read"]},"meta":{"api_version":"1","request_id":"9a60ee33-8a8d-4f10-a3f8-ffbb75ee8af6"}}
                    """.trimIndent()
                ),
                StubResponse(200, tokenEnvelope("access-2", "refresh-2")),
                StubResponse(204, "")
            )
        )
        val connections = mutableListOf<StubHttpURLConnection>()
        val api = HttpHumanAuthApi(BASE_URL) { url ->
            val response = responses.removeFirst()
            StubHttpURLConnection(url, response.status, response.body).also {
                connections += it
            }
        }

        val issued = api.issueToken("operator@example.test", "one-time-input", "test-device")
        val principal = api.currentPrincipal(issued.accessToken)
        val rotated = api.refresh(issued.refreshToken)
        api.logout(rotated.accessToken)

        assertEquals("access-1", issued.accessToken)
        assertEquals(Instant.parse("2027-01-15T08:15:00Z").toEpochMilli(), issued.accessExpiresAtEpochMillis)
        assertEquals("operator", principal.roleKey)
        assertEquals(listOf("university-lab"), principal.siteKeys)
        assertEquals("refresh-2", rotated.refreshToken)
        assertEquals(
            listOf(
                "$BASE_URL/auth/token",
                "$BASE_URL/me",
                "$BASE_URL/auth/refresh",
                "$BASE_URL/auth/logout"
            ),
            connections.map { it.url.toString() }
        )
        assertEquals(listOf("POST", "GET", "POST", "POST"), connections.map { it.requestMethod })
        assertEquals("test-device", connections[0].requestJson().getString("device_name"))
        assertEquals("Bearer access-1", connections[1].getRequestProperty("Authorization"))
        assertEquals("refresh-1", connections[2].requestJson().getString("refresh_token"))
        assertEquals("Bearer access-2", connections[3].getRequestProperty("Authorization"))
        assertTrue(connections.all { it.disconnected })
    }

    @Test
    fun problemDetailsBecomeASecretFreeStableAuthError() = runBlocking {
        val api = HttpHumanAuthApi(BASE_URL) { url ->
            StubHttpURLConnection(
                url,
                401,
                """{"status":401,"code":"auth.invalid_credentials","detail":"Authentication failed"}"""
            )
        }

        val failure = try {
            api.issueToken("operator@example.test", "one-time-input", "test-device")
            null
        } catch (error: AuthApiException) {
            error
        }

        assertTrue(failure != null)
        assertEquals(401, failure?.statusCode)
        assertEquals("auth.invalid_credentials", failure?.problemCode)
        assertEquals("Authentication request failed", failure?.message)
    }

    @Test
    fun authenticatedRequestsDoNotFollowRedirects() = runBlocking {
        lateinit var connection: StubHttpURLConnection
        var factoryCalls = 0
        val api = HttpHumanAuthApi(BASE_URL) { url ->
            factoryCalls += 1
            StubHttpURLConnection(url, 302, "").also { connection = it }
        }

        val failure = try {
            api.currentPrincipal("synthetic-access")
            null
        } catch (error: AuthApiException) {
            error
        }

        assertEquals(302, failure?.statusCode)
        assertEquals(1, factoryCalls)
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.disconnected)
    }

    private fun tokenEnvelope(access: String, refresh: String): String =
        """
        {"data":{"token_type":"Bearer","access_token":"$access","access_expires_at":"2027-01-15T08:15:00Z","refresh_token":"$refresh","refresh_expires_at":"2027-02-14T08:00:00Z"},"meta":{"api_version":"1","request_id":"9a60ee33-8a8d-4f10-a3f8-ffbb75ee8af6"}}
        """.trimIndent()

    private data class StubResponse(val status: Int, val body: String)

    private class StubHttpURLConnection(
        url: URL,
        private val status: Int,
        private val response: String
    ) : HttpURLConnection(url) {
        private val requestBytes = ByteArrayOutputStream()
        var disconnected = false
            private set

        override fun connect() = Unit
        override fun usingProxy(): Boolean = false

        override fun disconnect() {
            disconnected = true
        }

        override fun getResponseCode(): Int = status
        override fun getOutputStream(): OutputStream = requestBytes
        override fun getInputStream(): InputStream = responseStream()
        override fun getErrorStream(): InputStream? = responseStream()

        fun requestJson(): JSONObject = JSONObject(requestBytes.toString(Charsets.UTF_8.name()))

        private fun responseStream(): InputStream =
            ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val BASE_URL = "https://api.example.test/api/v1"
    }
}

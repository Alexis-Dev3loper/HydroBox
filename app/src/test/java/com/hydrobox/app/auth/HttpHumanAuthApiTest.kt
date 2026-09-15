package com.hydrobox.app.auth

import com.hydrobox.app.auth.session.AuthApiException
import com.hydrobox.app.auth.session.HttpHumanAuthApi
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.time.Instant

class HttpHumanAuthApiTest {
    @Test
    fun clientMatchesTheVersionedTokenProfileRefreshAndLogoutContract() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val observed = mutableListOf<String>()
        server.createContext("/api/v1/auth/token") { exchange ->
            observed += "token:${exchange.requestMethod}:${JSONObject(exchange.requestBody.reader().readText()).getString("device_name")}"
            exchange.json(200, tokenEnvelope("access-1", "refresh-1"))
        }
        server.createContext("/api/v1/me") { exchange ->
            observed += "me:${exchange.requestHeaders.getFirst("Authorization")}"
            exchange.json(
                200,
                """
                {"data":{"principal_uuid":"a57d5516-fcf8-4bfe-8c89-c480c11e2bdb","principal_type":"user","name":"Hydro Operator","email":"operator@example.test","role_key":"operator","status_key":"active","site_keys":["university-lab"],"scopes":["profile:read"]},"meta":{"api_version":"1","request_id":"9a60ee33-8a8d-4f10-a3f8-ffbb75ee8af6"}}
                """.trimIndent()
            )
        }
        server.createContext("/api/v1/auth/refresh") { exchange ->
            observed += "refresh:${JSONObject(exchange.requestBody.reader().readText()).getString("refresh_token")}"
            exchange.json(200, tokenEnvelope("access-2", "refresh-2"))
        }
        server.createContext("/api/v1/auth/logout") { exchange ->
            observed += "logout:${exchange.requestHeaders.getFirst("Authorization")}"
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()

        try {
            val api = HttpHumanAuthApi("http://127.0.0.1:${server.address.port}/api/v1")
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
                    "token:POST:test-device",
                    "me:Bearer access-1",
                    "refresh:refresh-1",
                    "logout:Bearer access-2"
                ),
                observed
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun problemDetailsBecomeASecretFreeStableAuthError() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/auth/token") { exchange ->
            exchange.json(
                401,
                """{"status":401,"code":"auth.invalid_credentials","detail":"Authentication failed"}"""
            )
        }
        server.start()

        try {
            val api = HttpHumanAuthApi("http://127.0.0.1:${server.address.port}/api/v1")
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
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun authenticatedRequestsDoNotFollowRedirects() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var redirectedRequests = 0
        server.createContext("/api/v1/me") { exchange ->
            exchange.responseHeaders.add(
                "Location",
                "http://127.0.0.1:${server.address.port}/unexpected"
            )
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/unexpected") { exchange ->
            redirectedRequests += 1
            exchange.json(200, "{}")
        }
        server.start()

        try {
            val api = HttpHumanAuthApi("http://127.0.0.1:${server.address.port}/api/v1")
            val failure = try {
                api.currentPrincipal("synthetic-access")
                null
            } catch (error: AuthApiException) {
                error
            }

            assertEquals(302, failure?.statusCode)
            assertEquals(0, redirectedRequests)
        } finally {
            server.stop(0)
        }
    }

    private fun tokenEnvelope(access: String, refresh: String): String =
        """
        {"data":{"token_type":"Bearer","access_token":"$access","access_expires_at":"2027-01-15T08:15:00Z","refresh_token":"$refresh","refresh_expires_at":"2027-02-14T08:00:00Z"},"meta":{"api_version":"1","request_id":"9a60ee33-8a8d-4f10-a3f8-ffbb75ee8af6"}}
        """.trimIndent()

    private fun HttpExchange.json(status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}

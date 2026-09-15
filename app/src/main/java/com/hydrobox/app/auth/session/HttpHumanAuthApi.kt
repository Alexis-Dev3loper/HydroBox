package com.hydrobox.app.auth.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

class HttpHumanAuthApi(
    private val baseUrl: String,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    }
) : HumanAuthApi {
    override suspend fun issueToken(
        email: String,
        password: String,
        deviceName: String
    ): TokenPair {
        val response = request(
            method = "POST",
            path = "auth/token",
            body = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("device_name", deviceName)
            }
        )
        return parseTokenPair(response)
    }

    override suspend fun currentPrincipal(accessToken: String): HumanPrincipal {
        val data = responseData(request("GET", "me", bearer = accessToken))
        val principalUuid = data.requireString("principal_uuid")
        runCatching { UUID.fromString(principalUuid) }
            .getOrElse { throw invalidResponse() }

        return HumanPrincipal(
            principalUuid = principalUuid,
            name = data.requireString("name"),
            email = data.requireString("email"),
            roleKey = if (data.isNull("role_key")) {
                null
            } else {
                data.getString("role_key").takeIf(String::isNotBlank)
            },
            siteKeys = data.getJSONArray("site_keys").strings(),
            scopes = data.getJSONArray("scopes").strings()
        )
    }

    override suspend fun refresh(refreshToken: String): TokenPair {
        val response = request(
            method = "POST",
            path = "auth/refresh",
            body = JSONObject().apply { put("refresh_token", refreshToken) }
        )
        return parseTokenPair(response)
    }

    override suspend fun logout(accessToken: String) {
        request("POST", "auth/logout", bearer = accessToken)
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        bearer: String? = null
    ): String = withContext(Dispatchers.IO) {
        val connection = connectionFactory(
            URL("${baseUrl.trimEnd('/')}/${path.trimStart('/')}")
        )
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.doInput = true
            connection.setRequestProperty("Accept", "application/json")
            bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }

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
                throw AuthApiException(status, problemCode(text))
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTokenPair(raw: String): TokenPair {
        val data = responseData(raw)
        if (data.requireString("token_type") != "Bearer") throw invalidResponse()

        return runCatching {
            TokenPair(
                accessToken = data.requireString("access_token"),
                accessExpiresAtEpochMillis = Instant.parse(
                    data.requireString("access_expires_at")
                ).toEpochMilli(),
                refreshToken = data.requireString("refresh_token"),
                refreshExpiresAtEpochMillis = Instant.parse(
                    data.requireString("refresh_expires_at")
                ).toEpochMilli()
            )
        }.getOrElse { throw invalidResponse() }
    }

    private fun responseData(raw: String): JSONObject = runCatching {
        JSONObject(raw).getJSONObject("data")
    }.getOrElse { throw invalidResponse() }

    private fun problemCode(raw: String): String = runCatching {
        JSONObject(raw).optString("code", "auth.request_failed")
    }.getOrDefault("auth.request_failed")

    private fun JSONObject.requireString(key: String): String =
        getString(key).takeIf(String::isNotBlank) ?: throw invalidResponse()

    private fun JSONArray.strings(): List<String> =
        (0 until length()).map { index -> getString(index) }

    private fun invalidResponse() = AuthApiException(502, "auth.invalid_response")

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val READ_TIMEOUT_MILLIS = 15_000
    }
}

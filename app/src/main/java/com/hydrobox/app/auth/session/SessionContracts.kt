package com.hydrobox.app.auth.session

data class TokenPair(
    val accessToken: String,
    val accessExpiresAtEpochMillis: Long,
    val refreshToken: String,
    val refreshExpiresAtEpochMillis: Long
)

data class HumanPrincipal(
    val principalUuid: String,
    val name: String,
    val email: String,
    val roleKey: String?,
    val siteKeys: List<String>,
    val scopes: List<String>
)

data class StoredSession(
    val localUserId: Long,
    val persistent: Boolean,
    val tokens: TokenPair
)

interface HumanAuthApi {
    suspend fun issueToken(email: String, password: String, deviceName: String): TokenPair
    suspend fun currentPrincipal(accessToken: String): HumanPrincipal
    suspend fun refresh(refreshToken: String): TokenPair
    suspend fun logout(accessToken: String)
}

interface SessionVault {
    suspend fun read(): StoredSession?
    suspend fun write(session: StoredSession)
    suspend fun clear()
}

class AuthApiException(
    val statusCode: Int,
    val problemCode: String
) : Exception("Authentication request failed") {
    val isTerminal: Boolean
        get() = statusCode == 401 || statusCode == 403 || statusCode == 409
}

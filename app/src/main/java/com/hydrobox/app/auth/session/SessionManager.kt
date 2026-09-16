package com.hydrobox.app.auth.session

import com.hydrobox.app.auth.data.AuthState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionManager(
    private val api: HumanAuthApi,
    private val vault: SessionVault,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = mutableState

    suspend fun login(
        email: String,
        password: String,
        deviceName: String,
        persistent: Boolean,
        persistPrincipal: suspend (HumanPrincipal) -> Long
    ): Boolean = mutex.withLock {
        if (email.isBlank() || password.isBlank()) return@withLock false

        var issued: TokenPair? = null
        try {
            val pair = api.issueToken(email.trim(), password, deviceName)
            issued = pair
            val principal = api.currentPrincipal(pair.accessToken)
            val localUserId = persistPrincipal(principal)
            val session = StoredSession(localUserId, persistent, pair)
            vault.write(session)
            mutableState.value = authenticatedState(localUserId, principal)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            issued?.let { bestEffort { api.logout(it.accessToken) } }
            vault.clear()
            mutableState.value = AuthState()
            false
        }
    }

    suspend fun restore(
        persistPrincipal: suspend (HumanPrincipal) -> Long
    ): Boolean = mutex.withLock {
        val stored = vault.read() ?: return@withLock loggedOut()

        if (!stored.persistent) {
            revokeBestEffort(stored)
            return@withLock clearAndLogOut()
        }

        val usable = ensureFresh(stored) ?: return@withLock false
        try {
            val principal = api.currentPrincipal(usable.tokens.accessToken)
            val localUserId = persistPrincipal(principal)
            if (localUserId != usable.localUserId) {
                vault.write(usable.copy(localUserId = localUserId))
            }
            mutableState.value = authenticatedState(localUserId, principal)
            true
        } catch (error: AuthApiException) {
            if (error.isTerminal) clearAndLogOut()
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun accessToken(): String? = mutex.withLock {
        val stored = vault.read() ?: return@withLock null
        val usable = ensureFresh(stored) ?: return@withLock null
        mutableState.value = mutableState.value.copy(
            isLoggedIn = true,
            userId = usable.localUserId
        )
        usable.tokens.accessToken
    }

    suspend fun invalidate() = mutex.withLock {
        try {
            vault.clear()
        } finally {
            loggedOut()
        }
    }

    suspend fun logout() = mutex.withLock {
        val stored = try {
            vault.read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (stored != null) revokeBestEffort(stored)
        clearAndLogOut()
    }

    private suspend fun ensureFresh(stored: StoredSession): StoredSession? {
        val now = nowEpochMillis()
        if (stored.tokens.refreshExpiresAtEpochMillis <= now) {
            clearAndLogOut()
            return null
        }

        if (stored.tokens.accessExpiresAtEpochMillis > now + ACCESS_EXPIRY_SKEW_MILLIS) {
            return stored
        }

        val rotated = try {
            api.refresh(stored.tokens.refreshToken)
        } catch (error: AuthApiException) {
            if (error.isTerminal) clearAndLogOut()
            return null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }

        val refreshed = stored.copy(tokens = rotated)
        return try {
            vault.write(refreshed)
            refreshed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Rotation invalidated the previous refresh token. Keeping it would
            // trigger reuse detection on the next attempt, so fail closed locally.
            clearAndLogOut()
            null
        }
    }

    private suspend fun revokeBestEffort(stored: StoredSession) {
        val now = nowEpochMillis()
        val accessToken = if (stored.tokens.accessExpiresAtEpochMillis > now) {
            stored.tokens.accessToken
        } else if (stored.tokens.refreshExpiresAtEpochMillis > now) {
            try {
                api.refresh(stored.tokens.refreshToken).accessToken
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        accessToken?.let { bestEffort { api.logout(it) } }
    }

    private fun loggedOut(): Boolean {
        mutableState.value = AuthState()
        return false
    }

    private fun authenticatedState(localUserId: Long, principal: HumanPrincipal) = AuthState(
        isLoggedIn = true,
        userId = localUserId,
        siteKeys = principal.siteKeys.distinct(),
        scopes = principal.scopes.toSet()
    )

    private suspend fun clearAndLogOut(): Boolean {
        vault.clear()
        return loggedOut()
    }

    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Local session removal remains mandatory even when the server is unreachable.
        }
    }

    companion object {
        private const val ACCESS_EXPIRY_SKEW_MILLIS = 30_000L
    }
}

package com.hydrobox.app.auth

import com.hydrobox.app.auth.session.AuthApiException
import com.hydrobox.app.auth.session.HumanAuthApi
import com.hydrobox.app.auth.session.HumanPrincipal
import com.hydrobox.app.auth.session.SessionManager
import com.hydrobox.app.auth.session.SessionVault
import com.hydrobox.app.auth.session.StoredSession
import com.hydrobox.app.auth.session.TokenPair
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SessionManagerTest {
    private val now = 1_800_000_000_000L
    private val principal = HumanPrincipal(
        principalUuid = "a57d5516-fcf8-4bfe-8c89-c480c11e2bdb",
        name = "Hydro Operator",
        email = "operator@example.test",
        roleKey = "operator",
        siteKeys = listOf("university-lab"),
        scopes = listOf("profile:read")
    )

    @Test
    fun loginPersistsOnlyIssuedTokensAfterProfileIsStored() = runBlocking {
        val api = FakeAuthApi(now, principal)
        val vault = FakeVault()
        val manager = SessionManager(api, vault) { now }

        val result = manager.login(
            email = principal.email,
            password = "one-time-input",
            deviceName = "test-device",
            persistent = true
        ) { received ->
            assertEquals(principal, received)
            42L
        }

        assertTrue(result)
        assertEquals(42L, vault.value?.localUserId)
        assertTrue(vault.value?.persistent == true)
        assertEquals(api.issued, vault.value?.tokens)
        assertEquals(42L, manager.state.value.userId)
        assertTrue(manager.state.value.isLoggedIn)
    }

    @Test
    fun rejectedLoginLeavesNoSessionOrPasswordMaterial() = runBlocking {
        val api = FakeAuthApi(now, principal).apply {
            issueFailure = AuthApiException(401, "auth.invalid_credentials")
        }
        val vault = FakeVault()
        val manager = SessionManager(api, vault) { now }

        assertFalse(
            manager.login(principal.email, "one-time-input", "test-device", true) { 42L }
        )
        assertNull(vault.value)
        assertFalse(manager.state.value.isLoggedIn)
    }

    @Test
    fun failedPrincipalLookupRevokesTheFreshlyIssuedFamily() = runBlocking {
        val api = FakeAuthApi(now, principal).apply {
            principalFailure = AuthApiException(401, "auth.token_revoked")
        }
        val vault = FakeVault()
        val manager = SessionManager(api, vault) { now }

        assertFalse(
            manager.login(principal.email, "one-time-input", "test-device", true) { 42L }
        )
        assertEquals(listOf(api.issued.accessToken), api.logoutTokens)
        assertNull(vault.value)
        assertFalse(manager.state.value.isLoggedIn)
    }

    @Test
    fun restoreUsesValidAccessTokenWithoutRefreshing() = runBlocking {
        val api = FakeAuthApi(now, principal)
        val vault = FakeVault(StoredSession(7L, true, api.issued))
        val manager = SessionManager(api, vault) { now }

        assertTrue(manager.restore { 7L })
        assertEquals(0, api.refreshCalls)
        assertEquals(1, api.principalCalls)
        assertEquals(7L, manager.state.value.userId)
    }

    @Test
    fun expiredAccessRotatesBothTokensBeforeRestoring() = runBlocking {
        val api = FakeAuthApi(now, principal)
        val expiredAccess = api.issued.copy(accessExpiresAtEpochMillis = now - 1)
        val vault = FakeVault(StoredSession(7L, true, expiredAccess))
        val manager = SessionManager(api, vault) { now }

        assertTrue(manager.restore { 7L })
        assertEquals(1, api.refreshCalls)
        assertEquals(api.rotated, vault.value?.tokens)
        assertEquals(api.rotated.accessToken, manager.accessToken())
    }

    @Test
    fun expiredRefreshIsRemovedWithoutCallingTheApi() = runBlocking {
        val api = FakeAuthApi(now, principal)
        val expired = api.issued.copy(
            accessExpiresAtEpochMillis = now - 1,
            refreshExpiresAtEpochMillis = now
        )
        val vault = FakeVault(StoredSession(7L, true, expired))
        val manager = SessionManager(api, vault) { now }

        assertFalse(manager.restore { 7L })
        assertNull(vault.value)
        assertEquals(0, api.refreshCalls)
        assertFalse(manager.state.value.isLoggedIn)
    }

    @Test
    fun terminalRefreshRejectionClearsTheLocalSession() = runBlocking {
        val api = FakeAuthApi(now, principal).apply {
            refreshFailure = AuthApiException(409, "auth.refresh_reused")
        }
        val expiredAccess = api.issued.copy(accessExpiresAtEpochMillis = now - 1)
        val vault = FakeVault(StoredSession(7L, true, expiredAccess))
        val manager = SessionManager(api, vault) { now }

        assertFalse(manager.restore { 7L })
        assertNull(vault.value)
        assertFalse(manager.state.value.isLoggedIn)
    }

    @Test
    fun transientRefreshFailureKeepsEncryptedRecoveryMaterial() = runBlocking {
        val api = FakeAuthApi(now, principal).apply {
            refreshFailure = IOException("offline")
        }
        val stored = StoredSession(7L, true, api.issued.copy(accessExpiresAtEpochMillis = now - 1))
        val vault = FakeVault(stored)
        val manager = SessionManager(api, vault) { now }

        assertFalse(manager.restore { 7L })
        assertEquals(stored, vault.value)
        assertFalse(manager.state.value.isLoggedIn)
    }

    @Test
    fun rotatedTokenPersistenceFailureClearsTheInvalidPreviousRefreshToken() = runBlocking {
        val api = FakeAuthApi(now, principal)
        val stored = StoredSession(7L, true, api.issued.copy(accessExpiresAtEpochMillis = now - 1))
        val vault = FakeVault(stored).apply { failWrites = true }
        val manager = SessionManager(api, vault) { now }

        assertFalse(manager.restore { 7L })
        assertEquals(1, api.refreshCalls)
        assertNull(vault.value)
        assertFalse(manager.state.value.isLoggedIn)
    }

    @Test
    fun revokedAccessDuringRestoreClearsTheLocalSession() = runBlocking {
        val api = FakeAuthApi(now, principal).apply {
            principalFailure = AuthApiException(401, "auth.token_revoked")
        }
        val vault = FakeVault(StoredSession(7L, true, api.issued))
        val manager = SessionManager(api, vault) { now }

        assertFalse(manager.restore { 7L })
        assertEquals(1, api.principalCalls)
        assertNull(vault.value)
        assertFalse(manager.state.value.isLoggedIn)
    }

    @Test
    fun offlinePrincipalValidationKeepsEncryptedRecoveryMaterial() = runBlocking {
        val api = FakeAuthApi(now, principal).apply {
            principalFailure = IOException("offline")
        }
        val stored = StoredSession(7L, true, api.issued)
        val vault = FakeVault(stored)
        val manager = SessionManager(api, vault) { now }

        assertFalse(manager.restore { 7L })
        assertEquals(stored, vault.value)
        assertFalse(manager.state.value.isLoggedIn)
    }

    @Test
    fun logoutRefreshesAnExpiredAccessThenRevokesAndAlwaysClearsLocalState() = runBlocking {
        val api = FakeAuthApi(now, principal)
        val stored = StoredSession(7L, true, api.issued.copy(accessExpiresAtEpochMillis = now - 1))
        val vault = FakeVault(stored)
        val manager = SessionManager(api, vault) { now }

        manager.logout()

        assertEquals(1, api.refreshCalls)
        assertEquals(listOf(api.rotated.accessToken), api.logoutTokens)
        assertNull(vault.value)
        assertFalse(manager.state.value.isLoggedIn)
    }

    @Test
    fun logoutClearsLocalSessionWhenTheServerIsOffline() = runBlocking {
        val api = FakeAuthApi(now, principal).apply { logoutFailure = IOException("offline") }
        val vault = FakeVault(StoredSession(7L, true, api.issued))
        val manager = SessionManager(api, vault) { now }

        manager.logout()

        assertEquals(listOf(api.issued.accessToken), api.logoutTokens)
        assertNull(vault.value)
        assertFalse(manager.state.value.isLoggedIn)
    }

    @Test
    fun sessionOnlyLoginIsRevokedInsteadOfRestoredAfterProcessStart() = runBlocking {
        val api = FakeAuthApi(now, principal)
        val vault = FakeVault(StoredSession(7L, false, api.issued))
        val manager = SessionManager(api, vault) { now }

        assertFalse(manager.restore { 7L })
        assertEquals(listOf(api.issued.accessToken), api.logoutTokens)
        assertNull(vault.value)
    }

    private class FakeVault(initial: StoredSession? = null) : SessionVault {
        var value: StoredSession? = initial
        var failWrites = false

        override suspend fun read(): StoredSession? = value

        override suspend fun write(session: StoredSession) {
            if (failWrites) throw IOException("storage unavailable")
            value = session
        }

        override suspend fun clear() {
            value = null
        }
    }

    private class FakeAuthApi(
        now: Long,
        private val principal: HumanPrincipal
    ) : HumanAuthApi {
        val issued = TokenPair("access-1", now + 900_000, "refresh-1", now + 2_592_000_000)
        val rotated = TokenPair("access-2", now + 900_000, "refresh-2", now + 2_592_000_000)
        var refreshCalls = 0
        var issueFailure: Exception? = null
        var refreshFailure: Exception? = null
        var principalFailure: Exception? = null
        var logoutFailure: Exception? = null
        var principalCalls = 0
        val logoutTokens = mutableListOf<String>()

        override suspend fun issueToken(email: String, password: String, deviceName: String): TokenPair {
            issueFailure?.let { throw it }
            return issued
        }

        override suspend fun currentPrincipal(accessToken: String): HumanPrincipal {
            principalCalls += 1
            principalFailure?.let { throw it }
            return principal
        }

        override suspend fun refresh(refreshToken: String): TokenPair {
            refreshCalls += 1
            refreshFailure?.let { throw it }
            return rotated
        }

        override suspend fun logout(accessToken: String) {
            logoutTokens += accessToken
            logoutFailure?.let { throw it }
        }
    }
}

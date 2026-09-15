package com.hydrobox.app.auth

import com.hydrobox.app.auth.data.AuthDao
import com.hydrobox.app.auth.data.AuthState
import com.hydrobox.app.auth.data.AuthStore
import com.hydrobox.app.auth.data.UserEntity
import com.hydrobox.app.auth.session.HumanPrincipal
import com.hydrobox.app.auth.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class AuthRepository(
    private val dao: AuthDao,
    private val store: AuthStore,
    private val sessions: SessionManager,
    private val deviceName: String
) {
    val authState: Flow<AuthState> = sessions.state.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUser: Flow<UserEntity?> =
        authState.flatMapLatest { st -> st.userId?.let { dao.observeById(it) } ?: flowOf(null) }
            .distinctUntilChanged()

    val lastEmail: Flow<String?> = store.lastEmail
    val rememberPref: Flow<Boolean> = store.rememberPref

    suspend fun login(email: String, pass: String, remember: Boolean): Boolean {
        val e = email.trim()
        if (e.isBlank() || pass.isBlank()) return false

        val loggedIn = sessions.login(e, pass, deviceName, remember, ::persistPrincipal)
        if (loggedIn) {
            try {
                store.rememberLogin(e, remember)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Remember-me metadata is optional and must not invalidate a secure session.
            }
        }
        return loggedIn
    }

    suspend fun logout() = sessions.logout()
    suspend fun restoreSession(): Boolean = sessions.restore(::persistPrincipal)

    suspend fun updateProfile(
        userId: Long,
        name: String,
        lastName: String,
        email: String,
        avatarUri: String?,
        phonePrefix: String?,
        phone: String?
    ) {
        val current = dao.findById(userId) ?: return
        val updated = current.copy(
            name = name,
            lastName = lastName,
            email = email,
            avatarUri = avatarUri,
            phonePrefix = phonePrefix,
            phone = phone
        )
        dao.update(updated)
    }

    private suspend fun persistPrincipal(principal: HumanPrincipal): Long {
        val existing = dao.findByPrincipalUuid(principal.principalUuid)
            ?: dao.findByEmail(principal.email)
        val names = principal.name.trim().split(Regex("\\s+"), limit = 2)
        val entity = existing?.copy(
            name = names.firstOrNull().orEmpty().ifBlank { principal.email.substringBefore('@') },
            lastName = names.getOrNull(1).orEmpty(),
            email = principal.email,
            principalUuid = principal.principalUuid,
            roleKey = principal.roleKey
        ) ?: UserEntity(
            name = names.firstOrNull().orEmpty().ifBlank { principal.email.substringBefore('@') },
            lastName = names.getOrNull(1).orEmpty(),
            email = principal.email,
            principalUuid = principal.principalUuid,
            roleKey = principal.roleKey
        )

        return dao.upsert(entity)
    }
}

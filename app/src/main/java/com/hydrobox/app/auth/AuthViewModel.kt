package com.hydrobox.app.auth

import android.app.*
import androidx.lifecycle.*
import com.hydrobox.app.auth.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import android.util.Log
import com.hydrobox.app.auth.session.AndroidKeystoreSessionVault
import com.hydrobox.app.auth.session.HttpHumanAuthApi
import com.hydrobox.app.auth.session.SessionManager
import com.hydrobox.app.api.DomainApiContextResolver
import com.hydrobox.app.api.HttpHydroDomainApi
import com.hydrobox.app.api.HydroDomainApi
import com.hydrobox.app.config.HydroBoxEnvironment
import java.io.File
import java.io.FileOutputStream

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val sessions = SessionManager(
        api = HttpHumanAuthApi(HydroBoxEnvironment.current.api.baseUrl),
        vault = AndroidKeystoreSessionVault(app)
    )
    private val repo = AuthRepository(
        dao = db.authDao(),
        store = AuthStore(app),
        sessions = sessions,
        deviceName = "HydroBox Android ${Build.MODEL}".take(100)
    )

    val domainApi: HydroDomainApi = HttpHydroDomainApi(
        baseUrl = HydroBoxEnvironment.current.api.baseUrl,
        contextProvider = {
            val accessToken = sessions.accessToken()
            DomainApiContextResolver.resolve(accessToken, sessions.state.value.siteKeys)
        },
        onUnauthorized = sessions::invalidate
    )

    val authState = repo.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = AuthState())

    val currentUser = repo.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    val lastEmail = repo.lastEmail
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
    val rememberPref = repo.rememberPref
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    private val _booting = MutableStateFlow(true)
    val booting: StateFlow<Boolean> = _booting

    init {
        viewModelScope.launch {
            try {
                repo.restoreSession()
            } finally {
                _booting.value = false
            }
        }
    }

    fun login(email: String, pass: String, remember: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) { repo.login(email, pass, remember) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            onResult(ok)
        }
    }

    private fun persistAvatarIfNeeded(source: String?): String? {
        if (source.isNullOrBlank()) return null
        val uri = source.toUri()
        if (uri.scheme.equals("file", true)) return source

        return try {
            val ctx = getApplication<Application>()
            val inStream = ctx.contentResolver.openInputStream(uri) ?: return null
            val dir = File(ctx.filesDir, "avatars").apply { mkdirs() }

            val uid = authState.value.userId ?: System.currentTimeMillis()
            val outFile = File(dir, "avatar_$uid.jpg")

            FileOutputStream(outFile).use { out ->
                inStream.use { inp -> inp.copyTo(out) }
            }
            Uri.fromFile(outFile).toString()
        } catch (e: Throwable) {
            Log.w("AuthVM", "persistAvatarIfNeeded failed", e)   // ← usa el parámetro
            null
        }
    }

    fun updateProfile(
        name: String,
        lastName: String,
        email: String,
        avatarUri: String?,
        phonePrefix: String?,
        phone: String?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = authState.value.userId ?: return@launch
            val persisted = persistAvatarIfNeeded(avatarUri)

            repo.updateProfile(
                userId = id,
                name = name,
                lastName = lastName,
                email = email,
                avatarUri = persisted ?: avatarUri,
                phonePrefix = phonePrefix,
                phone = phone
            )
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun logout() { viewModelScope.launch { repo.logout() } }
}

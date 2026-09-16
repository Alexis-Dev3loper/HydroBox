package com.hydrobox.app.auth.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreSessionVault(context: Context) : SessionVault {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override suspend fun read(): StoredSession? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val ciphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return@synchronized null
            val iv = preferences.getString(KEY_IV, null) ?: return@synchronized null

            runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey(),
                    GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP))
                )
                decode(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)))
            }.getOrElse {
                preferences.edit().clear().commit()
                null
            }
        }
    }

    override suspend fun write(session: StoredSession) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val ciphertext = cipher.doFinal(encode(session))
            check(
                preferences.edit()
                    .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                    .commit()
            ) { "Unable to persist encrypted session" }
        }
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        synchronized(lock) {
            preferences.edit().clear().commit()
            Unit
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(session: StoredSession): ByteArray = JSONObject().apply {
        put("version", FORMAT_VERSION)
        put("local_user_id", session.localUserId)
        put("persistent", session.persistent)
        put("access_token", session.tokens.accessToken)
        put("access_expires_at", session.tokens.accessExpiresAtEpochMillis)
        put("refresh_token", session.tokens.refreshToken)
        put("refresh_expires_at", session.tokens.refreshExpiresAtEpochMillis)
        put("principal_uuid", session.principalUuid ?: JSONObject.NULL)
        put("site_keys", JSONArray(session.siteKeys))
        put("scopes", JSONArray(session.scopes.toList()))
    }.toString().toByteArray(Charsets.UTF_8)

    private fun decode(bytes: ByteArray): StoredSession {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        check(json.getInt("version") == FORMAT_VERSION)
        return StoredSession(
            localUserId = json.getLong("local_user_id"),
            persistent = json.getBoolean("persistent"),
            tokens = TokenPair(
                accessToken = json.getString("access_token"),
                accessExpiresAtEpochMillis = json.getLong("access_expires_at"),
                refreshToken = json.getString("refresh_token"),
                refreshExpiresAtEpochMillis = json.getLong("refresh_expires_at")
            ),
            principalUuid = if (json.isNull("principal_uuid")) {
                null
            } else {
                json.optString("principal_uuid").takeIf(String::isNotBlank)
            },
            siteKeys = json.optJSONArray("site_keys").strings(),
            scopes = json.optJSONArray("scopes").strings().toSet()
        )
    }

    private fun JSONArray?.strings(): List<String> = buildList {
        val values = this@strings ?: return@buildList
        repeat(values.length()) { index ->
            values.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }

    companion object {
        const val PREFERENCES_NAME = "hydrobox_secure_session"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "hydrobox.mobile.session.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val FORMAT_VERSION = 1
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ciphertext"
    }
}

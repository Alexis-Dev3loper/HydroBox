package com.hydrobox.app.auth.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("auth_prefs")

class AuthStore(private val context: Context) {
    private val KEY_REMEMBER  = booleanPreferencesKey("remember_me")
    private val KEY_LAST_EMAIL= stringPreferencesKey("last_email")

    val lastEmail: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_EMAIL] }
    val rememberPref: Flow<Boolean> = context.dataStore.data.map { it[KEY_REMEMBER] ?: false }

    suspend fun rememberLogin(email: String, remember: Boolean) {
        context.dataStore.edit {
            it[KEY_REMEMBER] = remember
            if (remember) it[KEY_LAST_EMAIL] = email else it.remove(KEY_LAST_EMAIL)
        }
    }
}

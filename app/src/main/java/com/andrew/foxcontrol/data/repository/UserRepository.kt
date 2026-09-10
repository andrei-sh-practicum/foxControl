package com.andrew.foxcontrol.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class UserRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val USERNAME = stringPreferencesKey("username")
        private val PASSWORD_HASH = stringPreferencesKey("password_hash")
        private val AVATAR_URI = stringPreferencesKey("avatar_uri")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val username: Flow<String?> = context.dataStore.data
        .map { it[USERNAME] }

    val passwordHash: Flow<String?> = context.dataStore.data
        .map { it[PASSWORD_HASH] }

    val avatarUri: Flow<String?> = context.dataStore.data
        .map { it[AVATAR_URI] }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { it[ONBOARDING_COMPLETED] ?: false }

    suspend fun setUsername(username: String) {
        context.dataStore.edit { preferences ->
            preferences[USERNAME] = username
        }
    }

    suspend fun setPasswordHash(hash: String) {
        context.dataStore.edit { preferences ->
            preferences[PASSWORD_HASH] = hash
        }
    }

    suspend fun setAvatarUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[AVATAR_URI] = uri
        }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun isOnboardingCompleted(): Boolean {
        return context.dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }.first()
    }

    suspend fun initDefaultUser() {
        val currentUsername = username.firstOrNull()
        if (currentUsername.isNullOrEmpty()) {
            setUsername("12345")
            setPasswordHash("12345".hashCode().toString())
        }
    }
}

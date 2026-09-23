package com.andrew.foxcontrol.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.andrew.foxcontrol.core.util.AvatarImageUtil
import com.andrew.foxcontrol.data.local.dao.UserDao
import com.andrew.foxcontrol.data.local.entity.UserEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class UserRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userDao: UserDao
) {
    companion object {
        // DataStore keys (for password hash and onboarding — these remain)
        private val PASSWORD_HASH = stringPreferencesKey("password_hash")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    // ==================== Room-based user methods ====================

    /** Reactive single-user stream from Room. */
    val user: Flow<UserEntity?> = userDao.getUser()

    /** Ensures exactly one default user exists in Room. */
    suspend fun ensureDefaultUser() {
        if (userDao.getUserSync() == null) {
            userDao.insertUser(
                UserEntity(name = "username", passwordHash = "")
            )
        }
    }

    /** Update the user's name (always exactly one user). */
    suspend fun updateName(name: String): Result<Unit> = try {
        val existing = userDao.getUserSync()
            ?: return Result.failure(IllegalStateException("User not found"))
        userDao.updateName(existing.id, name)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Save an avatar: resize the image, write to private storage,
     * delete previous avatar file, and persist the new file:// URI in Room.
     */
    suspend fun saveAvatar(uri: Uri): Result<Unit> = try {
        val newPath = AvatarImageUtil.resizeAndSaveAvatar(context, uri)
            ?: return Result.failure(IllegalStateException("Failed to process avatar image"))

        // Delete previous avatar file
        val existing = userDao.getUserSync()
        existing?.avatarUri?.let { oldPath ->
            try {
                val oldFile = java.io.File(oldPath.removePrefix("file://"))
                oldFile.delete()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }

        // Persist new URI
        val userId = existing?.id ?: 0L
        userDao.updateAvatarUri(userId, newPath)

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ==================== DataStore methods (passwordHash, onboarding — remain unchanged) ====================

    val passwordHash: Flow<String?> = context.dataStore.data
        .map { it[PASSWORD_HASH] }

    suspend fun setPasswordHash(hash: String) {
        context.dataStore.edit { preferences ->
            preferences[PASSWORD_HASH] = hash
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
        val currentPasswordHash = passwordHash.first()
        if (currentPasswordHash.isNullOrEmpty()) {
            setPasswordHash("12345".hashCode().toString())
        }
    }
}

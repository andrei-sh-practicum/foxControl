package com.andrew.foxcontrol.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.andrew.foxcontrol.data.local.dao.*
import com.andrew.foxcontrol.data.local.entity.*

@Database(
    entities = [
        UserEntity::class,
        UsageSessionEntity::class,
        TrackedAppEntity::class,
        AppLimitEntity::class,
        GlobalLimitEntity::class,
        ServiceHeartbeatEntity::class,
        ServiceDowntimeEventEntity::class,
        AlertLogEntity::class,
        EmailRecipientEntity::class,
        EmailSettingsEntity::class,
        ReportSendLogEntity::class,
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        const val DATABASE_VERSION = 3
    }
    abstract fun userDao(): UserDao
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun trackedAppDao(): TrackedAppDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun globalLimitDao(): GlobalLimitDao
    abstract fun serviceHeartbeatDao(): ServiceHeartbeatDao
    abstract fun serviceDowntimeEventDao(): ServiceDowntimeEventDao
    abstract fun alertLogDao(): AlertLogDao
    abstract fun emailRecipientDao(): EmailRecipientDao
    abstract fun emailSettingsDao(): EmailSettingsDao
    abstract fun reportSendLogDao(): ReportSendLogDao
}

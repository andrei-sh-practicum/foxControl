package com.andrew.foxcontrol.di

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.room.Room
import com.andrew.foxcontrol.data.local.AppDatabase
import com.andrew.foxcontrol.data.local.AppDatabase.Companion.DATABASE_VERSION
import com.andrew.foxcontrol.data.local.MIGRATION_2_3
import com.andrew.foxcontrol.data.local.MIGRATION_3_4
import com.andrew.foxcontrol.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val TAG = "AppModule"
private const val DB_NAME = "foxcontrol_db"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DB_NAME
        )
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Singleton
    @Provides
    fun provideUserDao(appDatabase: AppDatabase): UserDao {
        return appDatabase.userDao()
    }

    @Singleton
    @Provides
    fun provideUsageSessionDao(appDatabase: AppDatabase): UsageSessionDao {
        return appDatabase.usageSessionDao()
    }

    @Singleton
    @Provides
    fun provideTrackedAppDao(appDatabase: AppDatabase): TrackedAppDao {
        return appDatabase.trackedAppDao()
    }

    @Singleton
    @Provides
    fun provideAppLimitDao(appDatabase: AppDatabase): AppLimitDao {
        return appDatabase.appLimitDao()
    }

    @Singleton
    @Provides
    fun provideGlobalLimitDao(appDatabase: AppDatabase): GlobalLimitDao {
        return appDatabase.globalLimitDao()
    }

    @Singleton
    @Provides
    fun provideServiceHeartbeatDao(appDatabase: AppDatabase): ServiceHeartbeatDao {
        return appDatabase.serviceHeartbeatDao()
    }

    @Singleton
    @Provides
    fun provideAlertLogDao(appDatabase: AppDatabase): AlertLogDao {
        return appDatabase.alertLogDao()
    }

    @Singleton
    @Provides
    fun providePackageManager(@ApplicationContext context: Context): PackageManager {
        return context.packageManager
    }

    @Singleton
    @Provides
    fun provideEmailRecipientDao(appDatabase: AppDatabase): EmailRecipientDao {
        return appDatabase.emailRecipientDao()
    }

    @Singleton
    @Provides
    fun provideEmailSettingsDao(appDatabase: AppDatabase): EmailSettingsDao {
        return appDatabase.emailSettingsDao()
    }

    @Singleton
    @Provides
    fun provideReportSendLogDao(appDatabase: AppDatabase): ReportSendLogDao {
        return appDatabase.reportSendLogDao()
    }
}

package com.andrew.foxcontrol.core.email

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.andrew.foxcontrol.data.repository.EmailRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

class EmailBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EmailBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        Log.d(TAG, "Boot received, checking email schedule...")

        // Use Hilt EntryPoint to get dependencies
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            EmailSchedulerEntryPoint::class.java
        )

        val scheduler = entryPoint.emailScheduler()
        val repository = entryPoint.emailRepository()

        try {
            val enabled = runBlocking { repository.getSetting("email_enabled") }
            if (enabled != "true") {
                Log.d(TAG, "Email reports are disabled, skipping schedule restoration")
                return
            }

            val hourStr = runBlocking { repository.getSetting("send_time_hour") }
            val minuteStr = runBlocking { repository.getSetting("send_time_minute") }

            val hour = hourStr?.toIntOrNull() ?: 8
            val minute = minuteStr?.toIntOrNull() ?: 0

            scheduler.scheduleDailyReport(hour, minute)
            Log.d(TAG, "Email report schedule restored for $hour:$minute")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore email schedule: ${e.message}", e)
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface EmailSchedulerEntryPoint {
    fun emailScheduler(): EmailScheduler
    fun emailRepository(): EmailRepository
}

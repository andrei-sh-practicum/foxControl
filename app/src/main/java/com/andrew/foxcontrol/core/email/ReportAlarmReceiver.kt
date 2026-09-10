package com.andrew.foxcontrol.core.email

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ReportAlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ReportAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm triggered - starting report send")

        val workRequest = OneTimeWorkRequestBuilder<SendReportWorker>()
            .setInitialDelay(0, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "immediate_report_send",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}

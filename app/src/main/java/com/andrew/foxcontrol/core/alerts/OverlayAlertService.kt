package com.andrew.foxcontrol.core.alerts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.andrew.foxcontrol.R
import android.graphics.PixelFormat

class OverlayAlertService : Service() {

    companion object {
        const val TAG = "OverlayAlertService"
        const val ACTION_SHOW = "com.andrew.foxcontrol.SHOW_ALERT"
        const val ACTION_HIDE = "com.andrew.foxcontrol.HIDE_ALERT"
        const val EXTRA_PACKAGE = "packageName"
        const val EXTRA_APP_NAME = "appName"
        const val EXTRA_LIMIT = "limit"
        const val EXTRA_USED = "used"
        const val OVERLAY_AUTO_HIDE_MS = 8000L // 8 seconds
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var autoHideHandler: Handler? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayAlertService created")
        autoHideHandler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
                val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""
                val limit = intent.getIntExtra(EXTRA_LIMIT, 0)
                val used = intent.getIntExtra(EXTRA_USED, 0)
                showAlert(packageName, appName, limit, used)
            }
            ACTION_HIDE -> {
                hideAlert()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hideAlert()
        autoHideHandler?.removeCallbacksAndMessages(null)
        autoHideHandler = null
    }

    private fun showAlert(packageName: String, appName: String, limit: Int, used: Int) {
        // P.4.3: hide previous overlay before showing new one (prevent view leak)
        hideAlert()

        try {
            if (windowManager == null) {
                windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }

            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_alert, null)
            val textAppName = overlayView?.findViewById<TextView>(R.id.tv_app_name)
            val textLimit = overlayView?.findViewById<TextView>(R.id.tv_limit)
            val textUsed = overlayView?.findViewById<TextView>(R.id.tv_used)

            textAppName?.text = "Превышен суточный лимит — $appName"
            textLimit?.text = "Лимит: $limit мин"
            textUsed?.text = "Использовано: $used мин"

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )

            windowManager?.addView(overlayView, params)
            Log.d(TAG, "Overlay alert shown for $appName")

            // P.4.1a: auto-hide after 8 seconds
            autoHideHandler?.postDelayed({
                hideAlert()
                Log.d(TAG, "Overlay alert auto-hidden after ${OVERLAY_AUTO_HIDE_MS / 1000}s")
            }, OVERLAY_AUTO_HIDE_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
            // Fallback to Toast
            Toast.makeText(this, "Лимит превышен!", Toast.LENGTH_LONG).show()
        }
    }

    private fun hideAlert() {
        try {
            overlayView?.let {
                windowManager?.removeView(it)
                overlayView = null
            }
            autoHideHandler?.removeCallbacksAndMessages(null)
            Log.d(TAG, "Overlay alert hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay", e)
        }
    }
}

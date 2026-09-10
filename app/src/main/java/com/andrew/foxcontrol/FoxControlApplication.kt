package com.andrew.foxcontrol

import android.app.Application
import com.andrew.foxcontrol.core.tracking.TrackingLogStorage
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FoxControlApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TrackingLogStorage.init(this)
    }
}

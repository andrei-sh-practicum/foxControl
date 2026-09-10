# R8 / ProGuard rules for Fox Control

# Keep Hilt classes
-keepnames class com.andrew.foxcontrol.** {
    @dagger.hilt.android.component.HiltAndroidApp *;
}

# Keep Room entities
-keep class * implements androidx.room.Entity

# Keep data classes
-keepclassmembers class * {
    @androidx.room.Entity *;
    @androidx.room.Dao *;
}

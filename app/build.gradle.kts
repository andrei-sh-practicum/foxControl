plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

import java.io.FileInputStream
import java.util.Properties

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        load(FileInputStream(localFile))
    }
}

android {
    namespace = "com.andrew.foxcontrol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.andrew.foxcontrol"
        minSdk = 26
        targetSdk = 35
        versionCode = 45
        versionName = "1.5.1"

        // SMTP defaults come from local.properties (not in git), see _docs/build_secrets.md
        val smtpLogin = localProperties.getProperty("SMTP_LOGIN", "")
        buildConfigField(
            "String",
            "SMTP_APP_PASSWORD_DEFAULT",
            "\"${localProperties.getProperty("SMTP_APP_PASSWORD", "CHANGE_ME_IN_PRODUCTION")}\""
        )
        buildConfigField("String", "SMTP_LOGIN_DEFAULT", "\"$smtpLogin\"")
        buildConfigField("String", "SMTP_FROM_DEFAULT", "\"${localProperties.getProperty("SMTP_FROM", smtpLogin)}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            // Rename APK to include version: app-debug_0.1.0.apk
            applicationVariants.all {
                val variant = this
                val versionName = variant.versionName
                variant.outputs.forEach { output ->
                    if (output is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                        output.outputFileName = "app-debug_${versionName}.apk"
                    }
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE"
        }
    }
}

// Room: export the DB schema (app/schemas/<db>/<version>.json) — base for migrations and migration tests
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Versions: gradle/libs.versions.toml

    // Core
    implementation(libs.androidx.core.ktx)

    // Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Room (KSP first so DAO is generated before Hilt processes it)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt (KSP after Room)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // DataStore (Preferences)
    implementation(libs.androidx.datastore.preferences)

    // Material Icons Extended
    implementation(libs.compose.material.icons.extended)

    // Coil (image loading for avatars)
    implementation(libs.coil.compose)

    // Jakarta Mail (email sending)
    implementation(libs.jakarta.mail.api)
    implementation(libs.angus.mail)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

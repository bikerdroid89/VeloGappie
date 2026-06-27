plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.velogappie.app"
    compileSdk = 35

    // Pinned debug signing key, committed to the repo: the phone and watch apps must always
    // share one stable certificate. Wear OS's Data Layer (Wearable API) ties its sync/delivery
    // registration to the installed app's signing cert — if the ambient ~/.android/debug.keystore
    // ever regenerates (e.g. a fresh machine/container), builds silently get a new certificate
    // and every data item delivery starts failing with no error surfaced to app code.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.velogappie.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "0.5.0"
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // Per-app language switching (AppCompatDelegate.setApplicationLocales) — works without
    // an AppCompatActivity, this app just calls Activity.recreate() itself after switching.
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Ride history: local-only persistence, never leaves the device.
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Health Connect: on-device IPC to the Health Connect provider app, not a network call.
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    // Optional Google Drive backup: the only network traffic this app ever makes, and only
    // when the user explicitly opts in. Goes straight to the user's own Drive over Google's
    // API — never through any third-party infrastructure.
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Interactive map for ride GPS routes (OpenStreetMap tiles via CARTO, no API key needed).
    implementation("org.maplibre.gl:android-sdk:13.3.0")
}

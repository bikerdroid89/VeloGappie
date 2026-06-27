plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.velogappie.wear"
    compileSdk = 35

    // Pinned debug signing key, committed to the repo: must match app/app's. See that
    // module's build.gradle.kts for why (Wear Data Layer ties delivery to the signing cert).
    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        // Must match app/app's applicationId exactly: the Wear Data Layer API only
        // recognizes a phone app and watch app as belonging together (and will deliver
        // DataItems/messages between them) when both share the same package name, version
        // code, and signing certificate. namespace stays com.velogappie.wear (Kotlin/R
        // class package) — only the installed package id changes.
        applicationId = "com.velogappie.app"
        minSdk = 30 // Wear OS 3+
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.wear.compose:compose-material:1.2.1")
    implementation("androidx.wear.compose:compose-foundation:1.2.1")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("androidx.wear:wear:1.4.0") // AmbientLifecycleObserver (always-on / dim-on-ambient support)
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-expression:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.guava:guava:33.3.1-android")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

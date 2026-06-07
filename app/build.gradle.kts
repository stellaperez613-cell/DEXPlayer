// build.gradle.kts (Module :app)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.dexplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.dexplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Deshabilitar tests
        testInstrumentationRunner = null
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

    // Deshabilitar unit tests
    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests.all { it.enabled = false }
    }
}

dependencies {
    implementation(libs.androidx.compose.material3.window.size.class1)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.animation.core)
    // ═══════════════════════════════════════════════════════════════
    // MEDIA3 / EXOPLAYER
    // ═══════════════════════════════════════════════════════════════
    val media3Version = "1.5.1"

    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")

    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3Version")
    implementation("androidx.media3:media3-datasource:$media3Version")
    implementation("androidx.media3:media3-extractor:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // ═══════════════════════════════════════════════════════════════
    // COMPOSE
    // ═══════════════════════════════════════════════════════════════
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.animation:animation:1.7.x")
    // ═══════════════════════════════════════════════════════════════
    // ANDROID CORE
    // ═══════════════════════════════════════════════════════════════
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // ═══════════════════════════════════════════════════════════════
    // DEBUG ONLY
    // ═══════════════════════════════════════════════════════════════
    debugImplementation("androidx.compose.ui:ui-tooling")
}

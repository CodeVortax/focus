// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 MR.English2008. See LICENSE.
import java.util.Properties

// Focus Study Mate: a separate app (com.barq.focus.ai). It is the only part of Focus that may use the internet.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.barq.focus.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.barq.focus.ai"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.1.3-beta"
    }

    // Same signing file as the main app (keystore.properties, git-ignored).
    val ksFile = rootProject.file("keystore.properties")
    val ks = Properties().also { if (ksFile.exists()) ksFile.inputStream().use { s -> it.load(s) } }
    signingConfigs {
        if (ksFile.exists()) create("release") {
            storeFile = rootProject.file(ks.getProperty("storeFile"))
            storePassword = ks.getProperty("storePassword")
            keyAlias = ks.getProperty("keyAlias")
            keyPassword = ks.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            if (ksFile.exists()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

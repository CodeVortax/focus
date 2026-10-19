// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 MR.English2008. See LICENSE.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.barq.focus"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.barq.focus"
        minSdk = 26
        targetSdk = 34
        versionCode = 37
        buildConfigField("String", "LICENSE_ID", "\"AGPL-3.0-only\"")
        buildConfigField("String", "LICENSE_NAME", "\"GNU Affero General Public License v3.0 only\"")
        versionName = "0.22.3-beta"
    }

    // Release signing: put your key details in keystore.properties (git-ignored, see docs/guides/signing.md).
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
}

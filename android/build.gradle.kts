// Module "android": the Android app, built on top of core.

import java.util.Properties

fun loadProperties(file: File): Properties = Properties().apply {
    if (file.exists()) file.inputStream().use { load(it) }
}

// The SNCF API key is never committed: it comes from local.properties (SNCF_API_KEY=...),
// or from resources/Timetables.properties (API_KEY=...) used by the desktop app.
val sncfApiKey: String =
    loadProperties(rootProject.file("local.properties")).getProperty("SNCF_API_KEY")
        ?: loadProperties(rootProject.file("resources/Timetables.properties")).getProperty("API_KEY")
        ?: ""

plugins {
    // The Android Gradle plugin (AGP). Its version is declared here, not in the root build,
    // so that tools that do not know Android (Eclipse) never need to load it.
    id("com.android.application") version "9.4.0"
}

android {
    namespace = "com.timetables.android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.timetables.android"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Exposed to the code as BuildConfig.SNCF_API_KEY.
        buildConfigField("String", "SNCF_API_KEY", "\"$sncfApiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    // core is compiled for Java 17, so this module must target Java 17 too.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

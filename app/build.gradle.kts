plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// version comes from the workflow (APP_VERSION, e.g. 1.8); versionCode always grows with it
val verName: String = System.getenv("APP_VERSION") ?: "1.0"
val verCode: Int = verName.split(".").let { 1000 + (it[0].toIntOrNull() ?: 1) * 10 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }

android {
    namespace = "com.namal.qmgstudio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.namal.qmgstudio"
        minSdk = 29
        targetSdk = 34
        versionCode = verCode
        versionName = verName
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("qmg") {
            storeFile = file("qmgstudio.jks")
            storePassword = "qmgstudio123"
            keyAlias = "qmgstudio"
            keyPassword = "qmgstudio123"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("qmg")
        }
        debug {
            signingConfig = signingConfigs.getByName("qmg")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

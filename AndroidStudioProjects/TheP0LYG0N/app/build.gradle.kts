plugins {
    id("com.android.application")
}

android {
    namespace = "com.polygon.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.polygon.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // No external dependencies — pure Android SDK WebView app
}
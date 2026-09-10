plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.leonid.unlock144bs"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.leonid.unlock144bs"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-poc"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
}

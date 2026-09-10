import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningFile.exists()) {
        releaseSigningFile.inputStream().use(::load)
    }
}

fun Properties.requiredSigningProperty(name: String): String =
    getProperty(name)?.takeIf(String::isNotBlank)
        ?: error("Missing '$name' in ${releaseSigningFile.name}")

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
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    if (releaseSigningFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(
                    releaseSigningProperties.requiredSigningProperty("storeFile"),
                )
                storePassword = releaseSigningProperties.requiredSigningProperty("storePassword")
                keyAlias = releaseSigningProperties.requiredSigningProperty("keyAlias")
                keyPassword = releaseSigningProperties.requiredSigningProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("com.google.android.material:material:1.13.0")

    testImplementation("junit:junit:4.13.2")
}

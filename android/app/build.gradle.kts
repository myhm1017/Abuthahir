plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.hearby"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.hearby"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "0.7"
    }

    // A private signing key for sideloading between the two of you.
    // Using the same key for every build lets new versions install over old ones.
    // Replace it with a secret key before any public or Play Store release.
    signingConfigs {
        create("sideload") {
            storeFile = file("../keystore/hearby-sideload.jks")
            storePassword = "hearby-sideload"
            keyAlias = "hearby"
            keyPassword = "hearby-sideload"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

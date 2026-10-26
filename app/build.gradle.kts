plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Version = GitHub Actions run number, so it matches the release tag v1.0.<run>
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "mv.ahusan.einthusantv"
    compileSdk = 34

    defaultConfig {
        applicationId = "mv.ahusan.einthusantv"
        minSdk = 24
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    // One fixed key for every build, so new versions install over old ones (in-app updates)
    signingConfigs {
        create("release") {
            storeFile = file("einthutv-release.jks")
            storePassword = "einthutv"
            keyAlias = "einthutv"
            keyPassword = "einthutv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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

dependencies {
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("androidx.core:core:1.12.0")
}

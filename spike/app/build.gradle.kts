plugins {
    id("com.android.application")
}

android {
    namespace = "dev.babysim.spike"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.babysim.spike"
        minSdk = 26
        versionCode = 1
        versionName = "m3-1"
    }

    // Two installs side by side: the same app targeting API 36 and API 37 (docs/03 §12).
    flavorDimensions += "target"
    productFlavors {
        create("t36") {
            dimension = "target"
            applicationIdSuffix = ".t36"
            targetSdk = 36
            resValue("string", "app_name", "M3 spike · target 36")
        }
        create("t37") {
            dimension = "target"
            applicationIdSuffix = ".t37"
            targetSdk = 37
            resValue("string", "app_name", "M3 spike · target 37")
        }
    }

    buildFeatures {
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

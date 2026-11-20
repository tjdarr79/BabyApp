plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.babysim.spike"
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 2
            }
    }

    defaultConfig {
        applicationId = "dev.babysim.spike"
        minSdk = 26
        versionCode = 4
        versionName = "m3-4"
    }

    // Two installs side by side: the same app targeting API 36 and API 37 (docs/03 §12).
    flavorDimensions += "target"
    productFlavors {
        create("t36") {
            dimension = "target"
            applicationIdSuffix = ".t36"
            targetSdk = 36
            resValue("string", "app_name", "Baby Sim extra test")
        }
        create("t37") {
            dimension = "target"
            applicationIdSuffix = ".t37"
            targetSdk = 37
            resValue("string", "app_name", "Baby Sim test")
        }
    }

    buildFeatures {
        resValues = true
        compose = true
    }

    // Testers install the shrunk release build (about 3 MB instead of 14 MB), debug-signed for sideloading.
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("roborazzi.test.record", "true") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Screenshot tests: render the screens on the JVM (no emulator needed). Output: app/build/outputs/roborazzi/.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.79.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.79.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

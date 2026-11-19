// M3 platform spike: throwaway app, NOT part of the main build (docs/07 M3). Build from this directory.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "baby-sim-m3-spike"
include(":app")

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    // v1: JVM only (consumed by :harness now, :app later). iOS targets are added at Step 11.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    compilerOptions {
        allWarningsAsErrors.set(true)
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

extensions.configure<DetektExtension> {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"), rootProject.file("config/detekt/engine.yml"))
    source.setFrom(
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
        "src/jvmMain/kotlin",
        "src/jvmTest/kotlin",
    )
}

tasks.named<Test>("jvmTest") {
    systemProperty(
        "engine.sourceRoot",
        layout.projectDirectory
            .dir("src")
            .asFile.absolutePath,
    )
}

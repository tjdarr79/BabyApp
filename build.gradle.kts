import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

val detektKotlinVersion = libs.versions.detektKotlin.get()

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }

    // Generated sources (e.g. bundled parameter files) are not hand-written; don't lint them.
    extensions.configure<KtlintExtension> {
        filter {
            exclude { it.file.path.contains("${File.separator}build${File.separator}") }
        }
    }

    // detekt 1.23.x must run on the Kotlin version it was compiled with, not the project's.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(detektKotlinVersion)
            }
        }
    }
}

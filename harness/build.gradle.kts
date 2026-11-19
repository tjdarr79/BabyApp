import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":engine"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("dev.babysim.harness.HarnessMainKt")
}

tasks.test {
    useJUnitPlatform()
    // Golden report files; regenerate deliberately with `./gradlew :harness:test -PupdateGolden=true`.
    systemProperty(
        "harness.goldenDir",
        layout.projectDirectory
            .dir("src/test/resources/golden")
            .asFile.absolutePath,
    )
    systemProperty("harness.updateGolden", providers.gradleProperty("updateGolden").getOrElse("false"))
    inputs.dir(layout.projectDirectory.dir("src/test/resources/golden")).optional()
}

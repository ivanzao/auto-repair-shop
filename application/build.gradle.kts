plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
    mainClass.set("br.com.soat.ApplicationKt")
}

tasks {
    shadowJar {
        archiveBaseName.set("application")
        archiveClassifier.set("")
        archiveVersion.set("")
        mergeServiceFiles()
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":api"))
    implementation(project(":storage"))

    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)

    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.yaml)

    // Logging
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jackson.module.kotlin)
    testImplementation(libs.jackson.databind)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.testcontainers.postgresql)
}
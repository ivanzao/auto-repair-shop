plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    group = "br.com.soat"
    version = "1.0"

    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        exclude("**/*IntegrationTest.*")
    }

    tasks.register<Test>("integrationTest") {
        useJUnitPlatform()
        excludes.clear()
        include("**/*IntegrationTest.*")
    }
}



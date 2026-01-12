import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    jacoco
    alias(libs.plugins.sonarqube)
}

repositories {
    mavenCentral()
}

sonar {
    properties {
        property("sonar.projectKey", "auto-repair-shop")
        property("sonar.projectName", "Auto Repair Shop")
        property("sonar.host.url", System.getenv("SONAR_HOST_URL") ?: "http://localhost:9000")
        property("sonar.token", System.getenv("SONAR_TOKEN") ?: "")

        // Coverage settings - use aggregated report
        property("sonar.coverage.jacoco.xmlReportPaths",
            "${project.layout.buildDirectory.get()}/reports/jacoco/jacocoAggregatedReport/jacocoAggregatedReport.xml")

        // Source and binary directories for all modules
        val allSources = subprojects.map { "${it.projectDir}/src/main/kotlin" }.joinToString(",")
        val allTests = subprojects.map { "${it.projectDir}/src/test/kotlin" }.joinToString(",")
        val allBinaries = subprojects.map { "${it.projectDir}/build/classes/kotlin/main" }.joinToString(",")

        property("sonar.sources", allSources)
        property("sonar.tests", allTests)
        property("sonar.java.binaries", allBinaries)

        // Exclude generated code and test fixtures
        property("sonar.exclusions", "**/build/**,**/*Fixtures.kt")
        property("sonar.test.exclusions", "**/build/**")

        // Language settings
        property("sonar.language", "kotlin")
        property("sonar.kotlin.source.version", "2.2")
        property("sonar.java.source", "21")
        property("sonar.java.target", "21")
    }
}

subprojects {
    group = "br.com.soat"
    version = "1.0"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "jacoco")

    repositories {
        mavenCentral()
    }

    // Configure JaCoCo for all subprojects
    configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
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

    // JaCoCo report for unit tests
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))

        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }

        executionData.setFrom(
            fileTree(project.layout.buildDirectory) {
                include("jacoco/test.exec")
            }
        )

        classDirectories.setFrom(
            files(
                fileTree(project.layout.buildDirectory) {
                    include("classes/kotlin/main/**")
                }
            )
        )

        sourceDirectories.setFrom(files("src/main/kotlin"))
    }

    // JaCoCo report for integration tests
    tasks.register<JacocoReport>("jacocoIntegrationTestReport") {
        dependsOn(tasks.named("integrationTest"))

        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }

        executionData.setFrom(
            fileTree(project.layout.buildDirectory) {
                include("jacoco/integrationTest.exec")
            }
        )

        classDirectories.setFrom(
            files(
                fileTree(project.layout.buildDirectory) {
                    include("classes/kotlin/main/**")
                }
            )
        )

        sourceDirectories.setFrom(files("src/main/kotlin"))
    }

    // Merged JaCoCo report combining test and integrationTest
    tasks.register<JacocoReport>("jacocoMergedReport") {
        dependsOn(tasks.named("test"), tasks.named("integrationTest"))

        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }

        // Merge execution data from both test tasks
        executionData.setFrom(
            fileTree(project.layout.buildDirectory) {
                include("jacoco/test.exec", "jacoco/integrationTest.exec")
            }
        )

        classDirectories.setFrom(
            files(
                fileTree(project.layout.buildDirectory) {
                    include("classes/kotlin/main/**")
                }
            )
        )

        sourceDirectories.setFrom(files("src/main/kotlin"))
    }
}

// Root-level aggregated report task
tasks.register<JacocoReport>("jacocoAggregatedReport") {
    dependsOn(subprojects.map { it.tasks.named("jacocoMergedReport") })

    group = "verification"
    description = "Generates an aggregated JaCoCo coverage report from all subprojects"

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)

        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoAggregatedReport/jacocoAggregatedReport.xml"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoAggregatedReport/html"))
    }

    // Aggregate execution data from all subprojects
    executionData.setFrom(
        subprojects.flatMap { subproject ->
            fileTree(subproject.layout.buildDirectory) {
                include("jacoco/test.exec", "jacoco/integrationTest.exec")
            }
        }
    )

    // Aggregate class directories from all subprojects
    classDirectories.setFrom(
        subprojects.flatMap { subproject ->
            files(
                fileTree(subproject.layout.buildDirectory) {
                    include("classes/kotlin/main/**")
                }
            )
        }
    )

    // Aggregate source directories from all subprojects
    sourceDirectories.setFrom(
        subprojects.map { subproject ->
            files("${subproject.projectDir}/src/main/kotlin")
        }
    )
}

// Make sonar task depend on aggregated report
tasks.named("sonar") {
    dependsOn(tasks.named("jacocoAggregatedReport"))
}
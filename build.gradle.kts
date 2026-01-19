import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    jacoco
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.owasp.dependencycheck)
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

        // Exclude generated code and test fixtures
        property("sonar.exclusions", "**/build/**,**/*Fixtures.kt")
        property("sonar.test.exclusions", "**/build/**")
        property("sonar.coverage.exclusions", "**/main/src/main/kotlin/**,**/KtorHttpServer.kt,**/*DTO.kt")

        // Use aggregated report that combines all .exec files with all classes
        property("sonar.coverage.jacoco.xmlReportPaths",
            "${layout.buildDirectory.get()}/reports/jacoco/jacocoAggregatedReport/jacocoAggregatedReport.xml")
    }
}

subprojects {
    group = "br.com.soat"
    version = "1.0"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "jacoco")
    apply(plugin = "org.sonarqube")

    // Each subproject uses the root aggregated report for coverage
    sonar {
        properties {
            property("sonar.coverage.jacoco.xmlReportPaths",
                "${rootProject.layout.buildDirectory.get()}/reports/jacoco/jacocoAggregatedReport/jacocoAggregatedReport.xml")
        }
    }

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

// OWASP Dependency-Check configuration for vulnerability scanning
dependencyCheck {
    formats = listOf("HTML", "JSON", "XML")
    outputDirectory = layout.buildDirectory.dir("reports/dependency-check").get().asFile.absolutePath
    scanConfigurations = listOf("runtimeClasspath", "compileClasspath")
    failBuildOnCVSS = 7.0f // Fail build on high/critical vulnerabilities

    analyzers {
        ossIndexEnabled = false // Disable OSS Index (requires auth, NVD is sufficient)
    }
}
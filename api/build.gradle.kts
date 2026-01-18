dependencies {
    implementation(project.dependencies.platform(libs.koin.bom))

    implementation(project(":domain"))

    implementation(libs.koin.ktor)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.logging)

    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.datetime)
    implementation(libs.postgres.driver)

    implementation(libs.hikari)

    // Flyway
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // Logging
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}
dependencies {
    implementation(project(":domain"))

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.datetime)
    implementation(libs.postgres.driver)

    implementation(libs.hikari)

    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}
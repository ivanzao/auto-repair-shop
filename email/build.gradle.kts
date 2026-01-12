dependencies {
    implementation(project(":domain"))

    implementation(libs.slf4j.api)
    implementation(libs.mailersend.sdk)
    runtimeOnly(libs.logback.classic)
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.slf4j.api)
    implementation(libs.mailersend.sdk)
    runtimeOnly(libs.logback.classic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

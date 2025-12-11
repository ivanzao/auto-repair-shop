dependencies {
    implementation(project(":domain"))

    implementation(libs.slf4j.api)
    implementation(libs.java.jwt)
    runtimeOnly(libs.logback.classic)
}

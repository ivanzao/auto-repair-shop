dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
    implementation(libs.bouncycastle.provider)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.jackson.databind)

    runtimeOnly(libs.logback.classic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jackson.module.kotlin)
    testRuntimeOnly(libs.junit.platform.launcher)
}

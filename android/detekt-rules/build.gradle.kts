plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    compileOnly(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

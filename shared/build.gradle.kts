plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.gson)
    testImplementation(libs.junit)
}

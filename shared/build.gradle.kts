plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Байткод Java 17, как в :app и :wear; собирается любым установленным JDK 17+,
// без обязательного отдельного toolchain JDK 17.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    implementation(libs.gson)
    testImplementation(libs.junit)
}

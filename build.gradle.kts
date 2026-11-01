plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
}

dependencies {
    implementation("org.mozilla:rhino:1.7.15")
    implementation("org.json:json:20240303")
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
}

repositories { mavenCentral() }

dependencies {
    implementation("org.mozilla:rhino:1.7.15")
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }

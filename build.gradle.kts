plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "2.3.12"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
}

group = "com.papaguycodes"
version = "0.0.1"

application {
    mainClass.set("com.papaguycodes.driver_license_generator.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    
    // ZXing Library for PDF417 Barcode Generation
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    implementation("ch.qos.logback:logback-classic:1.4.14")
}

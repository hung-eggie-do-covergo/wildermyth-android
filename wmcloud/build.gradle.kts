plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.2.2"
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("in.dragonbra:javasteam:1.8.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.google.protobuf:protobuf-java:4.31.1")
    implementation("com.google.zxing:core:3.5.3")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
}

kotlin { jvmToolchain(17) }
application { mainClass.set("wmcloud.MainKt") }

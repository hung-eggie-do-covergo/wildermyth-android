plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("in.dragonbra:javasteam:1.8.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.google.protobuf:protobuf-java:4.31.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.bouncycastle:bcprov-jdk18on:1.82")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
}

kotlin { jvmToolchain(17) }
application { mainClass.set("wmcloud.MainKt") }

// BouncyCastle's jar signature is invalid once merged into the fat jar.
tasks.shadowJar { exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA") }

// Versions live here so the same build script also works inside the app build,
// where AGP already provides the Kotlin plugin.
pluginManagement {
    plugins {
        kotlin("jvm") version "2.2.21"
        id("com.gradleup.shadow") version "9.2.2"
    }
}
rootProject.name = "wmcloud"

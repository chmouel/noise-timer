plugins {
    // AGP 9's built-in Kotlin support means the separate kotlin-android
    // plugin is no longer needed (or allowed); only the Compose compiler
    // plugin is still required.
    id("com.android.application") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
}

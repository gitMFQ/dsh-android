plugins {
    id("com.android.application") version "8.13.2" apply false
    // L1: Kotlin 1.9.24 -> 2.4.21. Compose compilation moved from AGP's
    // `composeOptions.kotlinCompilerExtensionVersion` to this first-party plugin
    // in Kotlin 2.0; see app/build.gradle.kts for the other half of that move.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

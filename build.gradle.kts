plugins {
    // AGP 9.0+ has Kotlin support built in: it compiles Kotlin itself, so the
    // org.jetbrains.kotlin.android plugin must NOT be applied any more (AGP fails
    // the build if it is). The Compose compiler plugin is still ours to apply.
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

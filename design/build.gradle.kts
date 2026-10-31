// The app's visual identity: the palette, the type scale, the spacing and radius
// inventories, and the `DshTheme` composable that puts them in a CompositionLocal.
//
// A module of its own because the theme is the one piece of UI that *everything*
// depends on and that depends on nothing: today it needs only compose-runtime, the
// material3 scheme types and `ThemeMode` from `:core:config`. Keeping it in `:app`
// meant phase 2's re-skin (docs/DESIGN-TOKENS.md) would edit a file that 71 other
// files import, in the same module that also holds `DshViewModel`'s 9 000 lines and
// every screen — so a palette change and a screen change could never be separate
// commits with a meaningful diff. Here they can be.
//
// It deliberately carries the material3 dependency rather than only compose-runtime:
// `DshTheme` builds a `ColorScheme` so that the handful of Material components in use
// inherit the app's accent, and `Typography` is the type scale's home. Both are
// ordinary library artifacts, so this stays a plain Android library.
plugins {
    id("com.android.library")
    // Required, and easy to miss: this module *declares* @Composable functions
    // (`DshTheme`), unlike `:core:model`, which only carries the @Immutable stability
    // annotation and therefore compiles without it. Without the Compose compiler the
    // build fails at code generation on `LocalDshColors.current` — an inline accessor
    // that only the plugin can lower:
    //   "Couldn't inline method call: ... <get-current>(): T of CompositionLocal"
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Distinct from the Kotlin package, as in the other core modules: the theme
    // ships no resources and no BuildConfig.
    namespace = "uk.xa0.dsh.core.design"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // `api`: `DshColors` and the `@Composable` entry point are this module's public
    // surface, and a consumer cannot call `DshTheme { }` or read `DshTheme.colors`
    // without the compose types those signatures mention.
    // `ThemeMode` is a plain `String` holder in :core:config — the only thing the
    // theme knows about the app, and the reason this module is not dependency-free.
    api(project(":core:config"))
    api(platform("androidx.compose:compose-bom:2026.09.00"))
    api("androidx.compose.runtime:runtime")
    api("androidx.compose.ui:ui-graphics")
    api("androidx.compose.ui:ui-text")
    // The scheme the theme maps onto, for components that paint themselves from it.
    api("androidx.compose.material3:material3")
}

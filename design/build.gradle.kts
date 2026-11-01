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
    // `KsuTokens.Motion` names spring/tween specs, whose types appear in the public
    // API, so this is `api` for the same reason as the rest.
    api("androidx.compose.animation:animation-core")

    // miuix, for one reason: `DshColors.toMiuixColors()` returns miuix's `Colors`, so the
    // type has to be on this module's API surface — `DshTheme` provides it to every screen
    // that draws a miuix component. `api`, not `implementation`, or a consumer could call
    // `DshTheme` but not name what it hands out.
    //
    // Version pinned here rather than taken from `:app`'s declaration: Gradle resolves the
    // highest and they are the same string, so the pin only exists to keep the theme's
    // contract readable in the file that states it. The app still declares miuix for the
    // components themselves; this line is about the palette bridge.
    //
    // Only `miuix-ui` is needed — `Colors` lives there. The preference/icons/squircle
    // modules are the app's business, not the theme's.
    api("top.yukonga.miuix.kmp:miuix-ui:0.9.4")

    // The liquid glass. `miuix-blur` declares **minSdk 33** (its own AndroidManifest says so),
    // while this app supports 26 — so the dependency is admitted under an explicit
    // `tools:overrideLibrary` in `:app`'s manifest rather than by pretending the floor moved.
    //
    // That is only honest because every call site is gated: the backdrop APIs are reached
    // exclusively through `LiquidGlass`, which falls back to a translucent fill with a
    // hairline stroke below API 33 (see `ui/glass/LiquidGlass.kt`). Nothing here is on a
    // path API 26-32 can execute.
    //
    // It is `api` because the glass composables this module now owns take `Backdrop` in
    // their signatures, and a consumer cannot call them without naming that type.
    api("top.yukonga.miuix.kmp:miuix-blur:0.9.4")

    // `WindowCompat.getInsetsController` — the system bars' icon appearance has three
    // different spellings across API 26..30+, and this is the one that covers all of them.
    // `implementation`, not `api`: no signature this module publishes mentions androidx.core,
    // so it is an implementation detail of `SystemBars`.
    implementation("androidx.core:core-ktx:1.13.1")

    // `dshPalette` is a pure function of its arguments, which is the point: the
    // Android window has no composition to read a token from, so the decision the
    // window and `DshTheme` share is testable on the JVM without one.
    testImplementation("junit:junit:4.13.2")
}

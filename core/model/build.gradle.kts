// The domain model: the types the protocol layer parses into and the UI draws.
//
// This is the first module to leave :app, and it is the innermost one: nothing in
// here imports another part of the app (see docs/research/13-module-extraction.md
// §1.1 — the `model → root` edge count is 0), so it can be compiled and tested on
// its own. That is the whole point of the split: 413 tests that used to need the
// application module's 58k lines now need only this one.
//
// It is an Android library, not a pure JVM module, for exactly one import:
// `model/TurnProcess.kt` logs a dropped duplicate transcript row through
// `android.util.Log`. That is a behaviour, not a dependency worth changing in a
// pure-move PR, so the module takes the Android plugin. `androidx.compose.runtime`
// is needed for `@Immutable`, which is a stability *annotation*, not UI.
plugins {
    // AGP 9 has Kotlin support built in: adding `org.jetbrains.kotlin.android`
    // here makes the build fail outright. The Compose compiler plugin is also not
    // applied — this module declares no @Composable function, only the stability
    // annotations, which are ordinary Kotlin metadata.
    id("com.android.library")
}

android {
    // Deliberately NOT `uk.xa0.dsh.model`. The Kotlin package keeps its old name
    // (see below), but the Android namespace decides where `R` and `BuildConfig`
    // land, and nothing here uses either. Keeping it distinct means the module
    // cannot accidentally start depending on :app's generated classes.
    namespace = "uk.xa0.dsh.core.model"
    compileSdk = 37
    // The build-tools AGP defaults to are not installed on the development box and
    // there is no writable SDK root to download into; :app pins the same value for
    // the same reason.
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 26
    }

    // This module ships no resources, no manifest and no BuildConfig. Turning them
    // off keeps the generated surface empty so the namespace above stays unused.
    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // Same Robolectric home as :app. These tests are plain JUnit and never start
    // Robolectric, but `android.util.Log` on a unit-test classpath is the stub that
    // throws, so anything reaching `TurnProcess`'s duplicate-row log needs the
    // Android runtime on the classpath rather than a mock.
    tasks.withType<Test>().configureEach {
        val fakeHome = rootProject.layout.projectDirectory.dir(".toolchain/robolectric-home").asFile
        doFirst { fakeHome.mkdirs() }
        systemProperty("user.home", fakeHome.absolutePath)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // `api`, not `implementation`: `@Immutable` appears on types in this module's
    // public API (ChatEntry, DisplayRow, ...), so :app must see the annotation's
    // metadata to keep proving those types stable. With `implementation` the
    // annotation is not on :app's compile classpath and every one of these types
    // silently becomes unstable — the exact regression RecomposeCostTest exists to
    // catch, except it would then fail to compile rather than merely get slower.
    api(platform("androidx.compose:compose-bom:2026.09.00"))
    api("androidx.compose.runtime:runtime")

    // The model's own code is suspend-heavy (ApprovalAnswer, FileDownload,
    // QuestionWaits, WorkspaceFileLoader), so coroutines is part of its API too.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // org.json comes from the Android platform at runtime and from the stub in
    // android.jar on a unit-test classpath — and the stub throws "Stub!" from every
    // constructor, which would fail every parser test for a reason that has nothing
    // to do with the parser. :app already carries this override for the same
    // reason; the module has to carry its own copy.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

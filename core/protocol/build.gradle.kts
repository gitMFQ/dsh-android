// The protocol layer: how the app talks to a DSH host over HTTP, WebSocket and the
// terminal stream, and how it decides which address to talk to.
//
// This is the third module out of `:app`, and the largest extraction so far — the
// `net/` package is some 3 900 lines and, unlike `:core:model` and `:core:term`, it is
// consumed from every other layer of the app. The order it was extracted in is not
// the order the roadmap first proposed: `net/` used to import `data.DshConfig` while
// `data/` imported `net.CertTrust`, so the two packages formed a cycle and neither
// could move first. `:core:config` (M2.5) is what broke it — `DshConfig` and the
// certificate types left `data/` and `net/` respectively, and the import that used to
// point into `:app` now points into that module. See
// docs/research/13-module-extraction.md §2.10.
//
// It is an Android library, not a pure JVM module, because `net/` is not pure:
// `DshClient` binds `android.net.ConnectivityManager`/`Network`/`LinkProperties` for
// route watching, `PersistentCookieJar` takes a `Context`, and three files log
// through `android.util.Log`. That is a property of the layer, not an accident, so
// the Android plugin is the honest choice rather than something to work around.
plugins {
    id("com.android.library")
}

android {
    // Distinct from the Kotlin package, as in the other core modules: this module
    // ships no resources, no manifest and no BuildConfig, so the namespace has
    // nothing to name.
    namespace = "uk.xa0.dsh.core.protocol"
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // Same Robolectric home as :app: the moved tests resolve `android-all` through a
    // Maven cache and a lock file under the user's home, neither of which is writable
    // in the build sandbox.
    tasks.withType<Test>().configureEach {
        val fakeHome = rootProject.layout.projectDirectory.dir(".toolchain/robolectric-home").asFile
        doFirst { fakeHome.mkdirs() }
        systemProperty("user.home", fakeHome.absolutePath)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // The rest of the app compiles with these; the protocol layer is where
        // `select`/`flatMapLatest`-style flow APIs are reached for first.
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

dependencies {
    // `api` for all three: each appears in a public signature of this module.
    // `DshClient.config` is a public `var DshConfig` and `applyConfig` takes one;
    // the endpoint resolver and mux work in `model.*` types; `TerminalClient` and
    // `RouterReadoutTransport` expose `term.*`. With `implementation` a consumer
    // could not call `DshClient.applyConfig(...)` at all — its parameter type would
    // not be on the caller's compile classpath.
    api(project(":core:model"))
    api(project(":core:term"))
    api(project(":core:config"))

    // The HTTP/WebSocket stack itself. `api` rather than `implementation` because
    // `DshClient` hands callers okhttp types (`Call`, `Response`, `WebSocket`) and
    // `CertPins.pinner` returns a `CertificatePinner`.
    api("com.squareup.okhttp3:okhttp:4.12.0")

    // `parseBalance` and the router readout parsers live in :core:model, so this is
    // only needed for the platform's `org.json`, which is a stub on a unit-test
    // classpath — the stub throws from every constructor, which would fail each
    // parser test for a reason unrelated to the parser. Same override as :app.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

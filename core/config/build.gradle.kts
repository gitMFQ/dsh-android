// What this client remembers about a host, and what it has been told to trust.
//
// This module is the answer to a question the extraction notes got wrong first: what
// goes in a "config" module is decided by the *dependency closure*, never by how the
// type's name reads. `DshConfig` sounds like a plain data class and would nominally
// belong beside the rest of the domain model, but its closure reaches
// `:core:term` (`TerminalCellSize`) and `:core:model` (`DrawerGrouping`). Putting it
// in `:core:model` would therefore have made the innermost module depend on the
// terminal module — the reverse of the direction `:core:term` was extracted to
// guarantee. See docs/research/13-module-extraction.md §2.7 / §2.8.
//
// It is an Android library, not a pure JVM module, for that same closure: `DshConfig`
// and `AcceptedCert` have no `import android` between them, but they compile against
// `:core:model`, which is an Android library, and a plain `kotlin("jvm")` consumer
// cannot match its variants (§2.6 — the second time that rule decided a module's
// plugin).
//
// ## Why there is no okhttp here
//
// `CertPins` came in with `AcceptedCert` because `pin` is derived from the
// certificate, and every member of it is pure JDK — except `pinner()`, which builds
// an okhttp `CertificatePinner`. That one function is the only place the certificate
// code touches the protocol stack, so it stayed behind as an extension in `:app`
// (`uk.xa0.dsh.net.pinner`) rather than dragging okhttp into a configuration module.
// Call sites read the same as before; only the file it lives in moved.
//
// Likewise `encode`/`decode` for the accepted certificates: they lived in
// `CertTrust.companion object`, and `CertTrust` is trust management (KeyStore,
// TrustManager, okhttp) that belongs in `:core:protocol` at M3. They are JDK-only, so
// they live at this module's top level and `CertTrust` is left to arrive later
// without them. `ConfigStore` calls the new top-level names directly.
plugins {
    // AGP 9 supplies Kotlin; adding `org.jetbrains.kotlin.android` fails the build.
    id("com.android.library")
}

android {
    // Distinct from the Kotlin package on purpose, exactly as in :core:model: the
    // namespace only decides where `R`/`BuildConfig` land, and this module ships
    // neither, so an unused namespace is the honest one.
    namespace = "uk.xa0.dsh.core.config"
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
    // `api`, not `implementation`: `DshConfig` exposes `DrawerGrouping` and
    // `TerminalCellSize` as constructor and property types, so a consumer of this
    // module cannot call it without seeing both. Same reasoning as :core:model's
    // compose-runtime line.
    api(project(":core:model"))
    api(project(":core:term"))

    testImplementation("junit:junit:4.13.2")
}

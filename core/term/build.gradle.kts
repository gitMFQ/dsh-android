// The terminal's own half of the app: the VT parser, the attachment/sequence gates
// and the seat rules.
//
// This is a **pure JVM module** (`kotlin("jvm")`), not an Android library. The
// package has no `import android`, no `import androidx` and no `org.json` — its files
// say so in their own headers, and docs/research/13-module-extraction.md §2.6 records
// the scan that confirmed it. That matters beyond tidiness: it is what lets these
// 186 tests run on any JVM, on this build box included, without an SDK or an aapt2.
//
// It also has no `internal` declarations, so extracting it needed no visibility
// changes at all — unlike :core:model, which needed two (§2.2).
//
// ## Why ROUTER_READOUT_TERMINAL_ID lives here
//
// `isRouterReadoutTerminal` used to be in :core:model, beside the readout's parsing
// code, and this module imported it. That single import was the only thing keeping
// this module from being pure JVM: :core:model is an Android library, and a plain
// `kotlin("jvm")` module cannot consume one (Gradle's variant matching rejects it on
// `org.jetbrains.kotlin.platform.type`, and no attribute coercion fixes that without
// also asking for an `r-class-jar`). Recognising a *terminal* is a terminal rule, so
// the id and the predicate moved here with the rules that use them (§2.6) — a
// one-line design call in exchange for a dependency edge and a build plugin.
plugins {
    // A pure JVM module has no AGP to supply Kotlin, so it asks for the plugin
    // explicitly. The version is declared once in the root build; repeating it here
    // fails with "already on the classpath with an unknown version".
    kotlin("jvm")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Everything here is plain Kotlin; this keeps the compiler from warning
        // about the JVM default that AGP normally supplies for :app.
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Only JUnit: the module's own sources depend on nothing outside the Kotlin
    // standard library, and its tests only assert on the classes they build.
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    testLogging {
        // CI counts the XML; a local run should say plainly what ran.
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

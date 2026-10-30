plugins {
    // No `org.jetbrains.kotlin.android`: AGP 9 compiles Kotlin itself and
    // rejects the old plugin outright. The Compose compiler is still a separate
    // plugin and still versioned with Kotlin.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uk.xa0.dsh"
    compileSdk = 37

    // A release key can be supplied entirely through the environment, which is how
    // CI signs without a keystore in the repository:
    //   SIGNING_STORE_FILE, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS,
    //   SIGNING_KEY_PASSWORD
    // With none of them set — the normal local case — nothing is configured and
    // Gradle signs the release build with the debug key, so `assembleRelease`
    // still produces something installable instead of failing.
    val releaseStore = System.getenv("SIGNING_STORE_FILE")
    val releaseStoreFile = releaseStore?.takeIf { it.isNotBlank() }?.let { file(it) }
    if (releaseStoreFile?.exists() == true) {
        signingConfigs {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    // This box ships build-tools 35.0.0/36.0.0 but not AGP's default 34.0.0,
    // and there is no writable SDK root to auto-download into.
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "uk.xa0.dsh"
        minSdk = 26
        targetSdk = 37
        versionCode = 15
        versionName = "0.6.6"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Compose UI tests run on the JVM under Robolectric. Scrolling is the one
    // behaviour in this app that a unit test could not reach and a person on a
    // phone could only describe — "it jumps", "it fights my finger" — so it gets
    // a real list, real gestures and a real layout, deterministically and in
    // seconds, instead of an install per hypothesis.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // Robolectric resolves its `android-all` jar through a Maven cache and takes a
    // lock file, both under the user's home. That is unwritable inside the build
    // sandbox — the lock alone fails the run — and would be litter in a real home
    // anyway, so the test JVM is given a home of its own beside the toolchain,
    // where the download survives a `clean`.
    tasks.withType<Test>().configureEach {
        val fakeHome = rootProject.layout.projectDirectory.dir(".toolchain/robolectric-home").asFile
        doFirst { fakeHome.mkdirs() }
        systemProperty("user.home", fakeHome.absolutePath)
    }

    // No `composeOptions { kotlinCompilerExtensionVersion = ... }` here: since
    // Kotlin 2.0 the Compose compiler ships with the Kotlin plugin (see the
    // `org.jetbrains.kotlin.plugin.compose` application above), and AGP rejects
    // a hand-pinned extension version.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Null unless a key was supplied above, in which case this leaves
            // Gradle's own debug-key default in place.
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/INDEX.LIST",
        )
    }
}

// AGP 9 brings its own Kotlin support, and it still exposes a project-level
// `kotlin { }` extension (TaskManager creates the "kotlin-extension"
// configuration for it). Applying org.jetbrains.kotlin.android is what AGP 9
// refuses, not configuring Kotlin itself.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Miuix: the Apache-2.0 component library that supplies everything the
    // KernelSU-style design needs but cannot legally take from the GPL-3.0
    // reference repo (dialogs, sheets, switches, sliders, rails, refresh,
    // icons, squircles). miuix-blur is deliberately absent: its module minSdk is
    // 33 and this app supports 26, so the glass bar is drawn by hand instead.
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-squircle:0.9.4")

    // SVG previews. Android ships no SVG renderer, and a WebView is not an option in
    // this app, so the drawing comes from the one small library that does this and
    // nothing else. SVG is also text, which is why the panel offers both views.
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // The module's first test source set. The terminal's VT parser and its
    // attachment/sequence rules are verified against bytes here, because the device
    // half of this feature cannot be exercised from a build box.
    testImplementation("junit:junit:4.13.2")
    // A *real* org.json for unit tests. The android.jar on a unit-test classpath is
    // the stubbed one, so `JSONObject(...)` throws "Stub!" and every parser test would
    // fail for a reason that has nothing to do with the parser. The test source set
    // takes this jar over the stub, which makes JSON-shaped logic testable on the JVM
    // — which is the only place it can be tested at all.
    testImplementation("org.json:json:20240303")
    // The scroll harness: a real Compose layout and real touch input, on the JVM.
    // 4.17 is the release that understands Kotlin 2.4 class metadata; 4.13 predates
    // the Kotlin 2.x toolchain entirely and fails at class-parsing time.
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

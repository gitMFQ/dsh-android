plugins {
    // No `org.jetbrains.kotlin.android`: AGP 9 compiles Kotlin itself and
    // rejects the old plugin outright. The Compose compiler is still a separate
    // plugin and still versioned with Kotlin.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // Screenshot baselines: `recordRoborazziDebug` / `verifyRoborazziDebug` /
    // `compareRoborazziDebug`. The plugin only adds tasks; nothing is captured
    // unless a test calls `captureRoboImage()`.
    id("io.github.takahirom.roborazzi")
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

    // A **committed** debug keystore, and the only reason this repository carries a
    // private key at all. `app/debug.keystore` is the standard Android debug identity
    // (alias `androiddebugkey`, password `android`), and pinning it is what makes
    // consecutive debug builds share one signature — so `adb install -r` upgrades the
    // app in place instead of failing with INSTALL_FAILED_UPDATE_INCOMPATIBLE and
    // demanding an uninstall.
    //
    // Left to itself AGP signs each debug build with `$ANDROID_USER_HOME/debug.keystore`,
    // which it generates on demand. CI lays out a fresh `.toolchain/android-home` for
    // every job (the directory is gitignored), so every build got a brand-new random
    // key, hence a different signature, hence the uninstall-then-reinstall loop.
    //
    // This key is public and worthless by design: it is the well-known debug identity,
    // it is in git history, and it MUST NOT sign anything published. Release signing
    // still goes through `signingConfigs.release` (from SIGNING_STORE_FILE); see the
    // fallback in `buildTypes.release` below.
    signingConfigs {
        // `maybeCreate`, not `create`: AGP may already have registered a signing config
        // named "debug" (the one bound to the default keystore), and `create` would then
        // fail with "Cannot add a signing config with name 'debug' as it already exists".
        maybeCreate("debug").apply {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
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

        // The screenshot class is a *baseline* test, not a functional one: it
        // asserts nothing about behaviour, and its whole value is the image diff
        // that `verifyRoborazziDebug` runs. Letting it into the functional suite
        // would do two bad things at once — inflate the 1286-test count that
        // every PR quotes as evidence, and turn a pixel diff into a red "unit
        // test" with no image attached.
        //
        // It is switched on by a property rather than by `--tests`, because a
        // Gradle test filter cannot re-include what another filter excluded: the
        // `--tests 'uk.xa0.dsh.screenshot.*'` on the Roborazzi task was silently
        // overruled by the exclusion below and failed with "No tests found for
        // given includes". The gate passes -Pscreenshots instead; see verify.yml.
        if (!project.hasProperty("screenshots")) {
            filter {
                excludeTestsMatching("uk.xa0.dsh.screenshot.*")
            }
        }
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
            // A key supplied above wins. With none — the normal local case — this
            // falls back to the committed debug key explicitly, rather than leaving
            // AGP's implicit `$ANDROID_USER_HOME` debug keystore in charge, so an
            // `assembleRelease` without secrets is at least signed reproducibly.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            // Pin the signature to `app/debug.keystore`; see the signingConfigs block
            // above for why the key is committed.
            signingConfig = signingConfigs.getByName("debug")
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

// The material3 the BOM pins, and the material3 this app actually shipped, are not the same
// number — and the difference is a bug the reader found before any of us did.
//
// `miuix-ui` declares `material3-window-size-class`, which pulls, in a chain nobody wrote
// deliberately:
//
//   miuix-ui:0.9.4                              (Apache-2.0, a Maven dependency)
//     -> org.jetbrains.compose.material3:material3-window-size-class-android:1.12.0-alpha03
//       -> androidx.compose.material3:material3-window-size-class-android:1.5.0-alpha22
//         -> androidx.compose.material3:material3:1.5.0-alpha22
//
// Gradle resolves the *highest* requirement and a BOM's constraint does not beat a
// transitive one, so every build shipped **material3 1.5.0-alpha22** while the BOM said
// 1.4.0. Verified without a build: the debug APK's `classes16.dex` contains
// `NavigationDrawerKt$ModalNavigationDrawer$2$1`, which exists only in the alpha, and has
// zero hits for the 1.4.0-only sibling.
//
// That alpha is not cosmetic. Its `ModalNavigationDrawer` runs
// `LaunchedEffect(drawerState.isOpen) { focusRequester.requestFocus() }` — Material's
// "keyboard focus should go to the first element of the drawer" — and the first
// always-focusable child of ours is the search field (`clickable` is `SystemDefined` and
// is not focusable in touch mode; a `BasicTextField` is), so **opening the drawer raised
// the keyboard**. `SessionsDrawer` also carries a zero-size focus anchor to absorb that
// request, which stays useful as a guard if the graph drifts again.
//
// Excluding the artifact is safe because nothing references it: measured, zero classes
// across `miuix-ui`, `miuix-preference` and this app name the package. Excluded on the
// *configuration* rather than on one declaration, because the artifact arrives through
// several edges (`:design`'s `api(miuix-ui)` is one of them) and an exclude on a single
// dependency only removes that dependency's own transitive.
configurations.all {
    exclude(group = "org.jetbrains.compose.material3", module = "material3-window-size-class")
    exclude(group = "org.jetbrains.compose.material3", module = "material3-window-size-class-android")
    exclude(group = "androidx.compose.material3", module = "material3-window-size-class")
    exclude(group = "androidx.compose.material3", module = "material3-window-size-class-android")
}

dependencies {
    // The domain model, extracted so its 413 tests no longer need this module's
    // 58k lines to compile. The Kotlin package is still `uk.xa0.dsh.model`, so no
    // import in this module changed — only where the files live.
    implementation(project(":core:model"))

    // The terminal's VT parser and its attachment/seat rules. Pure JVM, so its 186
    // tests run without an SDK; :app only consumes the types. ROUTER_READOUT_TERMINAL_ID
    // and isRouterReadoutTerminal live here too — see §2.6 of the extraction notes.
    implementation(project(":core:term"))

    // What the client remembers about a host: DshConfig, and the certificates it has
    // been told to trust. A module of its own rather than part of :core:model because
    // its closure spans model and term both (§2.7/§2.8) — putting it in the innermost
    // module would have made :core:model depend on :core:term, the direction :core:term
    // was extracted to keep free. The Kotlin packages are unchanged, so no import here
    // was rewritten.
    implementation(project(":core:config"))

    // The protocol layer: HTTP, WebSocket, the terminal stream and route resolution.
    // Moved after :core:config for a reason recorded in that module's build file —
    // `net/` and `data/` used to import each other, and config is what broke the
    // cycle. Kotlin packages are unchanged, so no import in this module changed.
    implementation(project(":core:protocol"))

    // The palette, type scale and `DshTheme` — everything the re-skin in phase 2
    // touches. Extracted so a palette change and a screen change cannot be the same
    // commit. Kotlin package unchanged, so no import in this module changed.
    implementation(project(":design"))

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    // 1.12.0 is a floor, not a preference. miuix's `WindowListPopup` (what a settings
    // dropdown opens) registers a `NavigationBackHandler`, which reads
    // `LocalNavigationEventDispatcherOwner` and throws
    //
    //   IllegalStateException: No NavigationEventDispatcher was provided via
    //   LocalNavigationEventDispatcherOwner
    //
    // when nothing provides it. That local resolves through the view-tree tag
    // `view_tree_navigation_event_dispatcher_owner`, which only `ComponentActivity`
    // from **androidx.activity 1.12.0** onwards sets — it is the release that made
    // `ComponentActivity` implement `NavigationEventDispatcherOwner`. On 1.9.0 every
    // dropdown tap in the Settings sheet and on the Me page's settings tabs took the
    // process down, which is the reported crash.
    //
    // Verified by decompiling both artifacts: 1.9.0's `ComponentActivity` implements
    // `OnBackPressedDispatcherOwner` and not `NavigationEventDispatcherOwner`; 1.12.0's
    // implements both.
    implementation("androidx.activity:activity-compose:1.12.0")
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
    // icons, squircles). `miuix-blur` is declared in `:design` rather than here,
    // because the palette bridge and the glass are that module's business; it
    // arrives under `tools:overrideLibrary` and is gated on API 33 — see NOTICE.md.
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

    // Screenshot baselines. `roborazzi` records and compares PNGs from a unit
    // test, `roborazzi-compose` adds the `SemanticsNodeInteraction.captureRoboImage()`
    // overload that captures one composable rather than the whole window. Both are
    // test-only: neither ships in the APK.
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.77.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.77.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

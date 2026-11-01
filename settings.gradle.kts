/**
 * Whether to try the Aliyun mirrors **before** the canonical repositories.
 *
 * ## Why this is a switch and not a default
 *
 * This box cannot reach `dl.google.com` / `repo.maven.apache.org` reliably — a single jar
 * downloads, but Gradle's parallel resolver stalls, which is what put the mirrors first in
 * the first place (~1s responses here).
 *
 * The mirrors, however, cannot be a *hard* first choice. **Gradle has no "try this
 * repository, and carry on if it fails" option**: a repository that answers 5xx is marked
 * broken and *disabled for the rest of the build*, so one bad gateway from a mirror fails
 * every module that needed anything from it. Measured on CI: a single
 * `502 Bad Gateway` for `miuix-ui-android-0.9.4.pom` from
 * `maven.aliyun.com/repository/google` turned into 43 resolution failures and a red
 * `verify` run that had nothing to do with the commit.
 *
 * So the default is the order a *clean* network wants (canonical first, mirrors as
 * fallback), and the local entry point `tools/localbuild.sh` exports
 * `DSH_LOCAL_MIRRORS=1` to put the mirrors back in front on the one machine that needs it.
 * The cost of getting this backwards is asymmetric: too slow locally is annoying, too
 * flaky on CI is unrelated red builds.
 */
pluginManagement {
    // `pluginManagement` has to be this script's first *statement*, so this cannot be a
    // top-level val shared with the block below — and it is cheap enough to state twice.
    val localMirrorsFirst = System.getenv("DSH_LOCAL_MIRRORS") == "1"
    repositories {
        // Maven's own declaration order is what matters; Gradle walks it in order and
        // stops at the first repository that has the artifact.
        if (localMirrorsFirst) maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
        if (!localMirrorsFirst) maven("https://maven.aliyun.com/repository/gradle-plugin")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val localMirrorsFirst = System.getenv("DSH_LOCAL_MIRRORS") == "1"
    repositories {
        if (localMirrorsFirst) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        if (!localMirrorsFirst) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
    }
}

rootProject.name = "DSH"
include(":app")
include(":core:model")
include(":core:term")
include(":core:config")
include(":core:protocol")
include(":design")

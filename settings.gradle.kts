pluginManagement {
    repositories {
        // Builds on this box cannot reach dl.google.com / repo.maven.apache.org
        // reliably: single jars download but Gradle's parallel resolver stalls.
        // The Aliyun mirrors carry the same artifacts and answer in ~1s, so they
        // are listed first; the canonical repositories stay as fallbacks.
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "DSH"
include(":app")
include(":core:model")

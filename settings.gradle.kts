pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
/*
 * No GitHub Packages repository, same as BrightRolodex. The rest of the family pulls
 * `com.gios:light-common` for shake-to-report; GitHub Packages has no anonymous read even for a
 * public package, so a brand-new repo without GPR_USER / GPR_TOKEN secrets fails with
 * "Could not find com.gios:light-common", which reads exactly like a credentials bug. Everything
 * here resolves from Google and Maven Central.
 */
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // GeckoView: Firefox's engine as a library. The phone's WebView is Chromium 113 and Light
        // decides when that changes; this rides in the APK and updates when we do.
        maven { url = uri("https://maven.mozilla.org/maven2/") }
    }
}
rootProject.name = "WebTools"
include(":app")

pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
/*
 * `com.gios:light-common` (shake-to-report, the chip, the crash offer) comes from GitHub Packages,
 * which has no anonymous read even for a public package. CI has GITHUB_ACTOR / GITHUB_TOKEN with
 * `packages: read`; a laptop puts gpr.user / gpr.key in local.properties or the environment. An
 * unset repository secret arrives as an empty string, not null, hence the blank checks.
 */
fun secret(vararg names: String): String? =
    names.firstNotNullOfOrNull { System.getenv(it)?.takeUnless(String::isBlank) }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/gi-os/BrightCommon")
            credentials {
                username = secret("GH_PACKAGES_USER", "GPR_USER", "GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull ?: ""
                password = secret("GH_PACKAGES_TOKEN", "GPR_TOKEN", "GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.key").orNull ?: ""
            }
        }
        // GeckoView: Firefox's engine as a library. The phone's WebView is Chromium 113 and Light
        // decides when that changes; this rides in the APK and updates when we do.
        maven { url = uri("https://maven.mozilla.org/maven2/") }
    }
}
rootProject.name = "WebTools"
include(":app")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Fine-grained PAT with Issues read+write on gi-os/light-reports only. CI sets REPORT_TOKEN from
// the repo secret; a local build reads `reportToken=` from local.properties; a build with neither
// still collects reports on the phone and posts nothing.
val reportToken: String = System.getenv("REPORT_TOKEN")
    ?: run {
        val lp = rootProject.file("local.properties")
        if (lp.exists()) java.util.Properties().apply { lp.inputStream().use { load(it) } }.getProperty("reportToken") ?: "" else ""
    }

android {
    namespace = "com.gios.webtools"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.gios.webtools"
        minSdk = 29
        targetSdk = 35
        // CI overwrites both from the workflow run number; see .github/workflows/build.yml
        versionCode = 1
        versionName = "1.1.0"

        buildConfigField("String", "REPORT_TOKEN", "\"" + reportToken.replace("\\", "").replace("\"", "") + "\"")

        // The LPIII is arm64 only.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../keystore/webtools.jks")
            storePassword = "webtools"
            keyAlias = "webtools"
            keyPassword = "webtools"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Same committed key as debug, so either APK upgrades over the other.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // WebViewAssetLoader gives each bundled tool its own https origin, so storage is per tool
    // and file:// (opaque origin, no localStorage) is never used for a bundle.
    implementation("androidx.webkit:webkit:1.12.1")

    // The one camera thing in the app: reading a tool's QR code. Same library BrightPasses
    // and LightTip use for their key QR.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Engine.BROWSER: the phone's Chromium as a Custom Tab, for sites whose bot gate refuses
    // every embedded view.
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    // Android's org.json is a stub under unit tests; this is the real one so the QR parser
    // can be tested on the JVM.
    testImplementation("org.json:json:20240303")
}

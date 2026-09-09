import java.util.Properties

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
        if (lp.exists()) Properties().apply { lp.inputStream().use { load(it) } }.getProperty("reportToken") ?: "" else ""
    }

android {
    namespace = "com.gios.webtools"
    // 36, not 35: GeckoView 148 and the androidx it pulls compile against 36. AGP 8.7 warns and
    // builds; see android.suppressUnsupportedCompileSdk in gradle.properties.
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.gios.webtools"
        minSdk = 29
        targetSdk = 35
        // CI overwrites both from the workflow run number; see .github/workflows/build.yml
        versionCode = 1
        versionName = "2.0.0"

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

    // GeckoView's own native libraries are already page-aligned and meant to load straight from
    // the APK; extracting them would double the install footprint.
    packaging {
        jniLibs { useLegacyPackaging = false }
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

    // The engine. Firefox 148 for arm64 only (the LPIII is arm64); ~86 MB of the APK. 154+ wants
    // compileSdk 37 and AGP 9.1, which the rest of the family has not moved to.
    implementation("org.mozilla.geckoview:geckoview-omni-arm64-v8a:148.0.20260309125808")

    // The one camera thing in the app: reading a tool's QR code. Same library BrightPasses
    // and LightTip use for their key QR.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    // Android's org.json is a stub under unit tests; this is the real one so the QR parser
    // can be tested on the JVM.
    testImplementation("org.json:json:20240303")
}

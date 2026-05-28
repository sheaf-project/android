import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services) apply false
}

// FCM is .play-only. The google-services plugin requires google-services.json
// at configure time, so we only apply it when the file is present in the play
// source set. A fresh checkout without Firebase set up still builds: the
// play APK just won't have working push until the dev drops the JSON in
// place. The .open flavour never has push regardless.
val playFirebaseJson: java.io.File = layout.projectDirectory.file("src/play/google-services.json").asFile
if (playFirebaseJson.exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)

    // Once applied, the plugin auto-registers a process<Variant>GoogleServices
    // task for every variant, including the open ones, and fails if
    // src/open/google-services.json doesn't exist. Disable the open-flavour
    // tasks so the open build keeps working without a (pointless) stub JSON.
    afterEvaluate {
        tasks.matching {
            it.name.startsWith("processOpen") && it.name.endsWith("GoogleServices")
        }.configureEach { enabled = false }
    }
}

// Short git SHA of HEAD at configure time. Surfaced via BuildConfig so the
// in-app About row can prove which build is actually running on the device,
// independent of versionName. Falls back to "unknown" outside a git checkout
// (e.g. release tarball builds).
val gitCommitShort: String = runCatching {
    ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .redirectErrorStream(true)
        .start()
        .inputStream.bufferedReader().readText().trim()
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"

// Build timestamp (UTC) at configure time, also surfaced via BuildConfig.
// Same purpose as gitCommitShort but answers "when was this APK compiled" —
// useful for the "wait, did I actually install the new build" moment when
// the versionName hasn't ticked but commits have. Formatted as a short
// ISO-like UTC string for human scanning, not for machine parsing.
val buildTimestamp: String =
    LocalDateTime.now(ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'"))

// Latest annotated release tag (e.g. "0.1.14"), used as a sensible
// versionName fallback for local dev builds that don't pass
// -PversionName. CI still passes -PversionName explicitly and that
// always wins. Without this fallback every local build shipped as
// "0.1.0" forever, making it impossible to tell stale dev installs
// apart from current ones in About. Strips a leading "v" since tags
// here are "v0.1.14" but versionName wants "0.1.14".
val latestReleaseTag: String = runCatching {
    ProcessBuilder("git", "describe", "--tags", "--abbrev=0", "--match", "v*")
        .redirectErrorStream(true)
        .start()
        .inputStream.bufferedReader().readText().trim()
}.getOrNull()?.takeIf { it.isNotBlank() }?.removePrefix("v") ?: "0.1.0"

android {
    namespace = "systems.lupine.sheaf"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        // CI passes -PversionName; local dev builds derive from the most
        // recent git release tag with a "-dev" suffix so About never lies
        // about which release line the dev build descends from.
        versionName = providers.gradleProperty("versionName").orNull
            ?: "$latestReleaseTag-dev"
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommitShort\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTimestamp\"")
    }

    // Distribution split: `.play` ships to Google Play (Firebase / FCM, prod
    // signing); `.open` ships to GitHub Releases + IzzyOnDroid (no Google
    // proprietary deps, CI signing). Each lands as its own applicationId so
    // both can coexist on the same device for side-by-side testing.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            applicationId = "systems.lupine.sheaf"
        }
        create("open") {
            dimension = "distribution"
            applicationId = "systems.lupine.sheaf.open"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // No signingConfig here. CI's openRelease gets signed via
            // -Pandroid.injected.signing.* properties; CI's playRelease is
            // intentionally unsigned so it can be signed offline by the
            // YubiKey-resident production key. For local "run release"
            // workflows, use the debug buildType (assemblePlayDebug etc.)
            // which is auto-signed with the Android debug keystore.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    ksp(libs.moshi.kotlin.codegen)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    // WorkManager + Hilt integration
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Storage
    implementation(libs.datastore.preferences)

    // Biometric / device credential auth (app lock)
    implementation(libs.androidx.biometric)

    // Images
    implementation(libs.coil.compose)

    // Markdown rendering (Markwon under the hood — supports images via Coil)
    implementation(libs.compose.markdown)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Glance (home screen widget)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Wearable Data Layer
    implementation(libs.play.services.wearable)

    // Firebase Cloud Messaging: .play flavour only. FCM requires Google
    // Play Services and is paired with the google-services Gradle plugin
    // applied above when google-services.json is present. The .open
    // flavour ships without it; UnifiedPush is the planned alternative.
    "playImplementation"(platform(libs.firebase.bom))
    "playImplementation"(libs.firebase.messaging.ktx)
    // For Task<T>.await() on FirebaseMessaging.getInstance().token.
    "playImplementation"(libs.kotlinx.coroutines.play.services)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
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

android {
    namespace = "systems.lupine.sheaf"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.1.0"
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommitShort\"")
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
            signingConfig = signingConfigs.getByName("debug")
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

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}

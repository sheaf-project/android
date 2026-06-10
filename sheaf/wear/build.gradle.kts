plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Short git SHA of HEAD at configure time. Surfaced via BuildConfig so the
// in-app About row can prove which build is actually running on the watch.
val gitCommitShort: String = runCatching {
    ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .redirectErrorStream(true)
        .start()
        .inputStream.bufferedReader().readText().trim()
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"

android {
    namespace = "systems.lupine.sheaf.wear"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        // Form-factor index appended to phone's base*10 versionCode: phone=0,
        // wear=1. CI passes -PversionCode already multiplied by 10, so +1 here
        // lands wear at base*10+1. Standalone dev builds (no -PversionCode)
        // fall back to 1+1=2, which never reaches Play.
        versionCode = (providers.gradleProperty("versionCode").orNull?.toInt() ?: 1) + 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.1.0"
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommitShort\"")
    }

    // Mirror :app's distribution flavours. The wear and phone APKs share an
    // applicationId per-flavour so they ship as form-factor variants of one
    // Play listing. Kotlin/Java package stays systems.lupine.sheaf.wear via
    // the namespace above.
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)

    // Wear Compose
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    // Tiles
    implementation(libs.wear.tiles)
    implementation(libs.wear.tiles.material)
    implementation(libs.concurrent.futures)

    // Complications
    implementation(libs.wear.complications.data.source.ktx)

    // Wearable Data Layer
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // Networking
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)

    // Encrypted credential storage (access/refresh tokens at rest)
    implementation(libs.androidx.security.crypto)

    // Image loading
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}

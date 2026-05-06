plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "systems.lupine.sheaf.wear"
    compileSdk = 35

    defaultConfig {
        // Shares the phone app's applicationId so the two ship as form-factor
        // variants of one Play listing. Kotlin/Java package stays
        // systems.lupine.sheaf.wear via the namespace above.
        applicationId = if (providers.gradleProperty("openBuild").orNull == "true") {
            "systems.lupine.sheaf.open"
        } else {
            "systems.lupine.sheaf"
        }
        minSdk = 26
        targetSdk = 35
        // Wear gets phone versionCode + 1 so phone and wear can co-exist in
        // the same Play listing with distinct codes per APK/AAB.
        versionCode = (providers.gradleProperty("versionCode").orNull?.toInt() ?: 1) + 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.1.0"
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

    // Wearable Data Layer
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // Networking
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}

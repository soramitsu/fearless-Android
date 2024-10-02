plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
}

android {
    compileSdk = rootProject.ext["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.ext["minSdkVersion"] as Int
        targetSdk = rootProject.ext["targetSdkVersion"] as Int
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    namespace = "jp.co.soramitsu.feature_soracard_api"
}

dependencies {
    implementation(libs.bundles.coroutines)
    implementation(libs.kotlinx.serialization.json)

    implementation(projects.runtime)
    implementation(projects.featureWalletApi)
}



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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging { resources.excludes.add("META-INF/*") }

    namespace = "jp.co.soramitsu.feature_soracard_api"
}

dependencies {
    implementation(libs.bundles.coroutines)
    implementation(libs.kotlinx.serialization.json)

    implementation(projects.runtime)
    implementation(projects.featureWalletApi)
}



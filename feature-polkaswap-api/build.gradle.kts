plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-parcelize")
}

kotlin {
    jvmToolchain(11)
}

android {
    compileSdk = rootProject.ext["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.ext["minSdkVersion"] as Int
        targetSdk = rootProject.ext["targetSdkVersion"] as Int
    }

    namespace = "jp.co.soramitsu.feature_polkaswap_api"
}

dependencies {
    implementation(libs.bundles.coroutines)

    implementation(projects.runtime)
    implementation(projects.featureWalletApi)
    implementation(project(mapOf("path" to ":common")))
}



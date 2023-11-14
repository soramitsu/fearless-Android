plugins {
    id("com.android.library")
    id("kotlin-android")
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

    namespace = "jp.co.soramitsu.feature_success_api"
}

dependencies {
    implementation(libs.bundles.coroutines)

    implementation(projects.runtime)
}



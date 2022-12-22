plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    compileSdk = rootProject.ext["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.ext["minSdkVersion"] as Int
        targetSdk = rootProject.ext["targetSdkVersion"] as Int
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    namespace = "jp.co.soramitsu.feature_polkaswap_api"
}

dependencies {
    implementation(libs.bundles.coroutines)

    implementation(projects.runtime)
    implementation(projects.featureWalletApi)
}



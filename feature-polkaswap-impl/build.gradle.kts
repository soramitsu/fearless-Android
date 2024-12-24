plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    compileSdk = rootProject.ext["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.ext["minSdkVersion"] as Int
        targetSdk = rootProject.ext["targetSdkVersion"] as Int
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    composeOptions {
        kotlinCompilerExtensionVersion = rootProject.ext["composeCompilerVersion"] as String
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    namespace = "jp.co.soramitsu.feature_polkaswap_impl"
}

dependencies {
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.bundles.compose)
    implementation(libs.fragmentKtx)
    implementation(libs.material)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.xnetworking.lib.android)

//    api(libs.sharedFeaturesPoolsDep) {
//        exclude(module = "android-foundation")
//    }

    implementation(projects.androidFoundation)
    implementation(projects.coreDb)
    implementation(projects.common)
    implementation(projects.runtime)
    implementation(projects.featurePolkaswapApi)
    implementation(projects.featureWalletApi)
    implementation(projects.featureAccountApi)
}

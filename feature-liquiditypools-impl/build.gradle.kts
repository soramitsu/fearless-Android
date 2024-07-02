import groovy.lang.Closure

plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}
android {
    namespace = "jp.co.soramitsu.feature_liquiditypools_impl"
    compileSdk = rootProject.ext["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.ext["minSdkVersion"] as Int
        targetSdk = rootProject.ext["targetSdkVersion"] as Int
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
}

dependencies {
    implementation(projects.androidFoundation)
    implementation(projects.common)
    implementation(projects.runtime)
    implementation(projects.featurePolkaswapApi)

//    implementation(project(":feature-wallet-api"))
//
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.bundles.compose)
    implementation(libs.fragmentKtx)
    implementation(libs.material)
//    implementation(libs.sharedFeaturesCoreDep) {
//        exclude(module = "android-foundation")
//    }
//    implementation(libs.retrofit)
//    implementation(libs.gson)
//    implementation(libs.web3jDep) {
//        exclude(group = "org.java-websocket", module = "Java-WebSocket")
//    }
//
//    implementation(libs.converter.gson)
//    implementation(libs.converter.scalars)
//
    implementation(libs.navigation.compose)
    implementation(libs.sora.ui)
//
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:1.7.3")
//
    implementation(projects.featureLiquiditypoolsApi)
//    implementation(projects.featureNftApi)
    implementation(projects.featureAccountApi)
    implementation(projects.featureWalletApi)
    implementation(projects.featureWalletImpl)
////    implementation(projects.coreDb)
//    implementation(projects.coreApi)
//    implementation(kotlin("script-runtime"))
}

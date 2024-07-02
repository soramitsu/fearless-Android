plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-parcelize")
}

android {
    namespace = "jp.co.soramitsu.feature_liquiditypools_api"
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
}

dependencies {
    implementation(projects.androidFoundation)
    implementation(projects.featureAccountApi)
    implementation(projects.featureWalletApi)
    implementation(projects.featureWalletImpl)
    implementation(projects.featurePolkaswapApi)
    implementation(projects.common)
    implementation(projects.runtime)
    implementation("javax.inject:javax.inject:1")

    implementation(libs.bundles.coroutines)
    implementation(libs.sharedFeaturesCoreDep) {
        exclude(module = "android-foundation")
    }
}
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(projects.featureAccountApi)
    implementation(projects.featureWalletApi)
    implementation(projects.featureWalletImpl)
    implementation(projects.featurePolkaswapApi)
    implementation(projects.common)
    implementation(projects.runtime)
    implementation("javax.inject:javax.inject:1")

    implementation(libs.bundles.coroutines)
    implementation(libs.sharedFeaturesCoreDep)

    implementation(libs.xnetworking.basic)
    implementation(libs.xnetworking.sorawallet) {
        exclude(group = "jp.co.soramitsu.xnetworking", module = "basic")
    }
    implementation(libs.soramitsu.android.foundation)
}
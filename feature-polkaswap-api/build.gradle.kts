plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-parcelize")
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
        jvmTarget = JavaVersion.VERSION_21.toString()
    }

    namespace = "jp.co.soramitsu.feature_polkaswap_api"
}

dependencies {
    implementation(libs.bundles.coroutines)

    implementation(projects.runtime)
    implementation(projects.featureWalletApi)
    implementation(project(mapOf("path" to ":common")))

    implementation("javax.inject:javax.inject:1")

    implementation(libs.xnetworking.basic)
    implementation(libs.xnetworking.sorawallet) {
        exclude(group = "jp.co.soramitsu.xnetworking", module = "basic")
    }
}



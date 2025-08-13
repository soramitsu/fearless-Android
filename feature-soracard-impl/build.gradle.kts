plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}
apply(from = "../tests.gradle")
apply(from = "../scripts/secrets.gradle")

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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }


    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }

    namespace = "jp.co.soramitsu.feature_soracard_impl"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.bundles.compose)
    implementation(libs.fragmentKtx)
    implementation(libs.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.gson)
    implementation(libs.xnetworking.lib.android)

    implementation(libs.sora.ui)
    implementation(libs.sora.soracard)
    implementation(libs.soramitsu.android.foundation)

    implementation(projects.common)
    implementation(projects.runtime)
    implementation(projects.featureWalletApi)
    implementation(projects.featureAccountApi)
    implementation(projects.featureSoracardApi)
}

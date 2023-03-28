plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
}
apply(from = "../tests.gradle")
apply(from = "../scripts/secrets.gradle")

android {
    compileSdk = rootProject.ext["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.ext["minSdkVersion"] as Int
        targetSdk = rootProject.ext["targetSdkVersion"] as Int
    }

    buildTypes {
        release {

        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    composeOptions {
        kotlinCompilerExtensionVersion = rootProject.ext["composeCompilerVersion"] as String
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
    namespace = "jp.co.soramitsu.feature_soracard_impl"
}

dependencies {
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.bundles.compose)
    implementation(libs.fragmentKtx)
    implementation(libs.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.xnetworking.android)

    implementation(libs.sora.ui)
    implementation(libs.sora.card)
    {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }

    implementation(projects.common)
    implementation(projects.runtime)
    implementation(projects.featureWalletApi)
    implementation(projects.featureAccountApi) //todo check neediness
    implementation(projects.featureSoracardApi)
}

plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.compose")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }


    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }

    namespace = "jp.co.soramitsu.feature_success_impl"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Lifecycle annotation processor for ViewModel/SavedStateHandle integration
    ksp(libs.lifecycle.compiler)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.bundles.compose)
    implementation(libs.fragmentKtx)
    implementation(libs.material)

    implementation(projects.common)
    implementation(projects.runtime)
    implementation(projects.featureSuccessApi)
    implementation(projects.featureAccountApi)
    implementation(projects.featureWalletImpl)
}

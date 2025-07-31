plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.compose")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }


    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }
}

dependencies {
    implementation(projects.common)
    implementation(projects.runtime)
    implementation(projects.featurePolkaswapApi)
    implementation(projects.featureLiquiditypoolsApi)
    implementation(projects.featureAccountApi)
    implementation(projects.featureWalletApi)
    implementation(projects.featureWalletImpl)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.bundles.compose)
    implementation(libs.fragmentKtx)
    implementation(libs.material)
    implementation(libs.navigation.compose)
    implementation(libs.sora.ui)
    implementation(libs.soramitsu.android.foundation)
    implementation(libs.room.ktx)
}

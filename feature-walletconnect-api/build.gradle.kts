plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
}

android {
    namespace = "co.jp.soramitsu.feature_walletconnect_api"
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
}

dependencies {
    implementation(projects.runtime)
    implementation(projects.featureWalletApi)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.bundles.compose)
    implementation(libs.fragmentKtx)
    implementation(libs.material)

    implementation(platform(libs.reownBomDep))
    implementation(libs.reownCoreDep) {
        // Exclude JNA to prevent class duplication - JNA is added as direct dependency in app module
        exclude(group = "net.java.dev.jna")
    }
    implementation(libs.reownWalletKitDep) {
        // Exclude JNA to prevent class duplication - JNA is added as direct dependency in app module
        exclude(group = "net.java.dev.jna")
    }
}
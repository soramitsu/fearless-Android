plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlinx-serialization")
}
android {
    namespace = "co.jp.soramitsu.feature_walletconnect_impl"
    compileSdk = rootProject.ext["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.ext["minSdkVersion"] as Int
        targetSdk = rootProject.ext["targetSdkVersion"] as Int
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }


    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.bundles.compose)
    implementation(libs.fragmentKtx)
    implementation(libs.material)
    implementation(project(":core-chain"))

    implementation(libs.web3jDep)

    // The in-repo WalletConnect v2 client: BouncyCastle crypto, OkHttp WebSocket relay, JSON.
    implementation(libs.bouncycastle)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.lifecycle.process)
    // Reads the encrypted preferences the removed Reown SDK left behind, once, to carry its sessions over.
    implementation(libs.security.crypto)

    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)

    implementation(projects.featureAccountApi)
    implementation(projects.featureAccountImpl)

    implementation(projects.common)
    implementation(projects.runtime)
    implementation(projects.featureWalletconnectApi)
    implementation(projects.coreDb)
    implementation(projects.coreApi)
    implementation(projects.featureWalletApi)
    implementation(projects.featureWalletImpl)

    testImplementation(libs.junit)
    testImplementation(projects.testShared)
    testImplementation(libs.arch.core.testing)
}

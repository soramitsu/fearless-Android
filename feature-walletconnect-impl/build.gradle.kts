plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.compose")
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
    implementation(libs.sharedFeaturesCoreDep)

    implementation(libs.web3jDep)

    implementation(platform(libs.reownBomDep))
    implementation(libs.reownCoreDep) {
        // Exclude JNA to prevent class duplication - JNA is added as direct dependency in app module
        exclude(group = "net.java.dev.jna")
    }
    implementation(libs.reownWalletKitDep) {
        // Exclude JNA to prevent class duplication - JNA is added as direct dependency in app module
        exclude(group = "net.java.dev.jna")
        // Exclude WalletConnect Pay until 16 KB native builds are available
        exclude(group = "com.walletconnect", module = "pay")
    }

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
}

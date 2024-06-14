plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}
android {
    namespace = "co.jp.soramitsu.feature_walletconnect_impl"
    compileSdk = rootProject.ext["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.ext["minSdkVersion"] as Int
        targetSdk = rootProject.ext["targetSdkVersion"] as Int
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = rootProject.ext["composeCompilerVersion"] as String
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging { resources.excludes.add("META-INF/*") }
}

dependencies {
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.bundles.compose)
    implementation(libs.fragmentKtx)
    implementation(libs.material)
    implementation(libs.sharedFeaturesCoreDep)

    implementation(libs.web3jDep)

    implementation(platform(libs.walletconnectBomDep))
    implementation(libs.walletconnectCoreDep)
    implementation(libs.walletconnectWeb3WalletDep)

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
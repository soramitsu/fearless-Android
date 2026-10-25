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
        jvmTarget = JavaVersion.VERSION_21.toString()
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
    implementation(project(":core-chain"))

    implementation(project(":core-foundation"))
    implementation(project(":core-networking"))
}
plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "jp.co.soramitsu.feature_polkamarkt_impl"
    compileSdk = rootProject.ext["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.ext["minSdkVersion"] as Int
        targetSdk = rootProject.ext["targetSdkVersion"] as Int
    }

    buildFeatures { compose = true }

    sourceSets.getByName("test").resources.srcDir("../app/src/test/resources")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }
}

dependencies {
    implementation(projects.featurePolkamarktApi)
    implementation(projects.featurePolkaswapApi)
    implementation(projects.featureAccountApi)
    implementation(projects.featureWalletApi)
    implementation(projects.common)
    implementation(projects.coreApi)
    implementation(projects.runtime)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.bundles.compose)
    implementation(libs.fragmentKtx)
    implementation(libs.material)
    implementation(libs.okhttp)
    implementation(libs.gson)

    testImplementation(libs.junit)
}

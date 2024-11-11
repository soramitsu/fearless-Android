import groovy.lang.Closure

plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}
android {
    namespace = "jp.co.soramitsu.feature_nft_impl"
    compileSdk = rootProject.ext["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.ext["minSdkVersion"] as Int
        targetSdk = rootProject.ext["targetSdkVersion"] as Int

        buildConfigField("String", "ALCHEMY_API_KEY", readAlchemyApiKey())
    }

    buildTypes {
        release {

        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    composeOptions {
        kotlinCompilerExtensionVersion = rootProject.ext["composeCompilerVersion"] as String
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }
}

dependencies {
    implementation(project(":feature-wallet-api"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.bundles.compose)
    implementation(libs.fragmentKtx)
    implementation(libs.material)
    implementation(libs.sharedFeaturesCoreDep)
    implementation(libs.retrofit)
    implementation(libs.gson)
    implementation(libs.web3jDep) {
        exclude(group = "org.java-websocket", module = "Java-WebSocket")
    }

    implementation(libs.converter.gson)
    implementation(libs.converter.scalars)

    implementation(libs.navigation.compose)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:1.7.3")

    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)

    implementation(projects.common)
    implementation(projects.runtime)
    implementation(projects.featureNftApi)
    implementation(projects.featureAccountApi)
    implementation(projects.featureWalletImpl)
//    implementation(projects.coreDb)
    implementation(projects.coreApi)
    implementation(kotlin("script-runtime"))
}

fun readAlchemyApiKey(): String{
    return (rootProject.ext["readSecretInQuotes"] as Closure<String>).invoke("FL_ANDROID_ALCHEMY_API_ETHEREUM_KEY")
}
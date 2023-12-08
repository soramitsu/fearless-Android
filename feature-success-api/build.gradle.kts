plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    compileSdk = rootProject.ext["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.ext["minSdkVersion"] as Int
        targetSdk = rootProject.ext["targetSdkVersion"] as Int
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    namespace = "jp.co.soramitsu.feature_success_api"
}

dependencies {
    implementation(libs.bundles.coroutines)

    implementation(projects.runtime)
}



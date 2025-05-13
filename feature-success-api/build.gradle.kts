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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }

    namespace = "jp.co.soramitsu.feature_success_api"
}

dependencies {
    implementation(libs.bundles.coroutines)

    implementation(projects.runtime)
}



plugins {
    id("com.android.library")
    id("dagger.hilt.android.plugin")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "jp.co.soramitsu.feature_irohaconnect_impl"
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

    // Iroha Connect v1: BouncyCastle crypto (X25519, HKDF, ChaCha20-Poly1305, BLAKE2b/3, Ed25519) and the
    // OkHttp WebSocket relay; no SDK and no native code.
    implementation(libs.bouncycastle)
    implementation(libs.okhttp)
    implementation(libs.coroutines.core)

    implementation(projects.featureIrohaconnectApi)
    implementation(projects.featureAccountApi)
    implementation(projects.common)
    implementation(projects.runtimeUtils)

    testImplementation(libs.junit)
    testImplementation(projects.testShared)
    testImplementation(projects.runtime)
    testImplementation(projects.coreApi)
}

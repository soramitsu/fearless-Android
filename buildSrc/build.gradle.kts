plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()

    maven {
        url = uri("https://plugins.gradle.org/m2/")
    }
}

dependencies {
    implementation(libs.gradleplugins.android)
    implementation(libs.gradleplugins.detekt)
    implementation(libs.gradleplugins.kotlin)
    implementation(libs.gradleplugins.hiltAndroid) // NOTE: hilt wth buildSrc. https://github.com/google/dagger/issues/3068
}

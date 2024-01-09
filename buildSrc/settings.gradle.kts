enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "fearless-Android"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

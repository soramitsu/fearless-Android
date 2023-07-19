import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.DetektPlugin

plugins {
    id("io.gitlab.arturbosch.detekt")
}

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    detektPlugins(libs.findLibrary("detekt-formatting").get())
    detektPlugins(libs.findLibrary("detekt-cli").get())
}

tasks.register<Detekt>("detektAll") {
    setup(autoCorrect = false)
}

tasks.register<Detekt>("detektFormat") {
    setup(autoCorrect = true)
}

fun Detekt.setup(autoCorrect: Boolean) {
    description = "Detekt checking for all modules"
    parallel = true
    ignoreFailures = false
    this.autoCorrect = autoCorrect
    buildUponDefaultConfig = true
    setSource(file(projectDir))
    config.setFrom(files("$rootDir/detekt/detekt.yml"))
    include("**/*.kt")
    exclude("**/resources/**", "**/build/**")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "1.8"
}
tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "1.8"
}

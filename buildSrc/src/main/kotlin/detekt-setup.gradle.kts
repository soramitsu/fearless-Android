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

tasks.register<Detekt>("detektCheck") {
    description = "Detekt checking"
    setup(autoCorrect = false)
}

tasks.register<Detekt>("detektFormat") {
    description = "Detekt formatting"
    setup(autoCorrect = true)
}

val detektRulesPath = "$rootDir/android-foundation/src/main/java/jp/co/soramitsu/androidfoundation/detekt/rules.yml"

tasks.register<DetektCreateBaselineTask>("detektGenerateBaseline") {
    description = "Detekt generating baseline"
    parallel.set(true)
    ignoreFailures.set(false)
    autoCorrect.set(false)
    buildUponDefaultConfig.set(true)
    setSource(file(projectDir))
    baseline.set(file("$rootDir/detekt/baseline.xml"))
    config.setFrom(files(detektRulesPath))
    include("**/*.kt")
    exclude("**/resources/**", "**/build/**")
}

fun Detekt.setup(autoCorrect: Boolean) {
    parallel = true
    ignoreFailures = false
    this.autoCorrect = autoCorrect
    buildUponDefaultConfig = true
    setSource(file(projectDir))
    baseline.set(file("$rootDir/detekt/baseline.xml"))
    config.setFrom(files(detektRulesPath))
    include("**/*.kt")
    exclude("**/resources/**", "**/build/**")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "1.8"
}
tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "1.8"
}

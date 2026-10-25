package jp.co.soramitsu.common.compose.theme.fw

import android.content.res.Resources
import jp.co.soramitsu.common.R

data class FwFontLicense(val name: String, val text: String)

/** Original bundled notices remain available offline, including their copyright statements. */
fun loadFwFontLicenses(resources: Resources): List<FwFontLicense> = listOf(
    "Sora" to R.raw.fw_sora_ofl,
    "Inter" to R.raw.fw_inter_ofl,
    "JetBrains Mono" to R.raw.fw_jetbrains_mono_ofl
).map { (name, resource) ->
    FwFontLicense(name, resources.openRawResource(resource).bufferedReader(Charsets.UTF_8).use { it.readText() })
}

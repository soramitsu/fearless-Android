package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.R

/**
 * The brand line under the stacked lockup on the welcome and onboarding pages, as on the design's
 * Welcome board: the tagline in the display face, widely tracked, between two short gold rules.
 */
@Composable
fun FwBrandTagline(modifier: Modifier = Modifier) {
    val c = LocalFwColors.current
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val text = fwHudLabel(stringResource(R.string.fw_brand_tagline), language)
    // Wide tracking suits Latin capitals; other scripts keep a light tracking.
    val latin = isLatinDisplay(language)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 30.dp, height = 1.dp).background(c.brandGold))
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FwFont.display(language),
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                letterSpacing = if (latin) 0.36.em else 0.12.em
            ),
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Box(Modifier.size(width = 30.dp, height = 1.dp).background(c.brandGold))
    }
}

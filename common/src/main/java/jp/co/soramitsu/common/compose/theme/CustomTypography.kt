package jp.co.soramitsu.common.compose.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.compose.component.soraTextStyle

@Stable
data class CustomTypography(
    val header1: TextStyle,
    val header2: TextStyle,
    val header3: TextStyle,
    val header4: TextStyle,
    val header5: TextStyle,
    val header6: TextStyle,
    val body0: TextStyle,
    val body1: TextStyle,
    val body2: TextStyle,
    val body3: TextStyle,
    val capsTitle: TextStyle,
    val capsTitle2: TextStyle,
    val button: TextStyle
)

fun TextStyle.weight(fontWeight: FontWeight) = copy(fontWeight = fontWeight)
fun TextStyle.bold() = copy(fontWeight = FontWeight.Bold)
fun TextStyle.fontSize(fontSize: TextUnit) = copy(fontSize = fontSize)

val flwTypography = CustomTypography(
    header1 = soraTextStyle().bold().fontSize(30.sp),
    header2 = soraTextStyle().bold().fontSize(22.sp),
    header3 = soraTextStyle().bold().fontSize(18.sp),
    header4 = soraTextStyle().weight(FontWeight.W700).fontSize(16.sp),
    header5 = soraTextStyle().bold().fontSize(14.sp),
    header6 = soraTextStyle().bold().fontSize(12.sp),
    body0 = soraTextStyle().fontSize(16.sp).weight(FontWeight.Normal),
    body1 = soraTextStyle().fontSize(14.sp).weight(FontWeight.Normal),
    body2 = soraTextStyle().fontSize(12.sp).weight(FontWeight.Normal),
    body3 = soraTextStyle().fontSize(10.sp).weight(FontWeight.Normal),
    capsTitle = soraTextStyle().fontSize(12.sp).weight(FontWeight.W700),
    capsTitle2 = soraTextStyle().fontSize(10.sp).weight(FontWeight.W700),
    button = soraTextStyle().fontSize(14.sp).bold()
)

internal val FearlessTypography = staticCompositionLocalOf { flwTypography }

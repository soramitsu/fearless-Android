package jp.co.soramitsu.common.compose.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
    val button: TextStyle
)

val flwTypography = CustomTypography(
    header1 = TextStyle(
        fontFamily = Sora,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold
    ),
    header2 = TextStyle(
        fontFamily = Sora,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold
    ),
    header3 = TextStyle(
        fontFamily = Sora,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    ),
    header4 = TextStyle(
        fontFamily = Sora,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    ),
    header5 = TextStyle(
        fontFamily = Sora,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    ),
    header6 = TextStyle(
        fontFamily = Sora,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    ),
    body0 = TextStyle(
        fontFamily = Sora,
        fontSize = 16.sp,
        color = Color.White,
        background = Color.Black,
        fontWeight = FontWeight.Normal
    ),
    body1 = TextStyle(
        fontFamily = Sora,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal
    ),
    body2 = TextStyle(
        fontFamily = Sora,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal
    ),
    body3 = TextStyle(
        fontFamily = Sora,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal
    ),
    button = TextStyle(
        fontFamily = Sora,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
)

internal val FearlessTypography = staticCompositionLocalOf { flwTypography }

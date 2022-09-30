package jp.co.soramitsu.common.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

@Composable
fun FearlessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        FearlessColors provides flwColors,
        FearlessTypography provides flwTypography
    ) {
        MaterialTheme(
            colors = fearlessMaterialColors,
            content = content
        )
    }
}

@Composable
fun FearlessThemeBlackBg(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        FearlessColors provides flwColors,
        FearlessTypography provides flwTypography
    ) {
        MaterialTheme(
            colors = fearlessMaterialColors.copy(background = Color.Black.copy(alpha = 0.8f), surface = Color.Black.copy(alpha = 0.8f)),
            content = content
        )
    }
}

val MaterialTheme.customColors: CustomColors
    @Composable
    @ReadOnlyComposable
    get() = FearlessColors.current

val MaterialTheme.customTypography: CustomTypography
    @Composable
    @ReadOnlyComposable
    get() = FearlessTypography.current

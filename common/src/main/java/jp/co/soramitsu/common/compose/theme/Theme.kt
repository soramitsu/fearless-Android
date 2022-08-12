package jp.co.soramitsu.common.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

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

val MaterialTheme.customColors: CustomColors
    @Composable
    @ReadOnlyComposable
    get() = FearlessColors.current

val MaterialTheme.customTypography: CustomTypography
    @Composable
    @ReadOnlyComposable
    get() = FearlessTypography.current

package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.white24

@Composable
fun BackgroundCornered(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.customColors.backgroundBlurColor,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .background(backgroundColor, FearlessCorneredShape())
            .wrapContentSize()
    ) {
        content()
    }
}

@Composable
fun BackgroundCorneredWithBorder(
    modifier: Modifier = Modifier,
    backgroundColor: Color = black05,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .background(backgroundColor, FearlessCorneredShape())
            .border(1.dp, color = white24, shape = FearlessCorneredShape())
            .wrapContentSize()
    ) {
        content()
    }
}

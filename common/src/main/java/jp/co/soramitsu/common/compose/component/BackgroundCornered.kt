package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import jp.co.soramitsu.common.compose.theme.customColors

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

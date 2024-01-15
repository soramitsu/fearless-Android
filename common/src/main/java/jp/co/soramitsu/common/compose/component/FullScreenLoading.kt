package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black50

@Composable
fun FullScreenLoading(
    isLoading: Boolean,
    contentAlignment: Alignment = Alignment.TopStart,
    BlurredContent: @Composable () -> Unit
) {
    val blurModifier = if (isLoading) Modifier.blur(10.dp) else Modifier
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(blurModifier)
        ) {
            BlurredContent()
        }
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(black50)
            )
            FearlessProgress(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
@Preview
private fun FullScreenLoadingPreview() {
    FearlessTheme {
        FullScreenLoading(isLoading = true) {
            Column {
                Row {
                    ButtonPreview()
                    ButtonPreview()
                }
                Row {
                    ButtonPreview()
                    ButtonPreview()
                }
            }
        }
    }
}

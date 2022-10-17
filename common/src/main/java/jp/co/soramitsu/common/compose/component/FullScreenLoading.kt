package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black50
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.white04

@Composable
fun FullScreenLoading(isLoading: Boolean, BlurredContent: @Composable () -> Unit) {
    if (!isLoading) return
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(10.dp)
        ) {
            BlurredContent()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(black50)
        )
        Box(
            modifier = Modifier
                .size(88.dp)
                .align(Alignment.Center)
                .background(white04, RoundedCornerShape(size = 16.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(2.dp)
                    .padding(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    color = colorAccentDark,
                    strokeWidth = 4.dp
                )

            }
            Image(
                res = R.drawable.ic_fearless_logo, tint = colorAccentDark, modifier = Modifier
                    .size(50.dp)
                    .align(Alignment.Center)
            )
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

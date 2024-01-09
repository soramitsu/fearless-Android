package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white08

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
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .align(Alignment.Center)
                    .background(grayButtonBackground.copy(alpha = 0.7f), RoundedCornerShape(size = 16.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .border(4.dp, white08, shape = CircleShape)
                        .background(transparent, shape = CircleShape)
                )
                CircularProgressIndicator(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .align(Alignment.Center),
                    color = colorAccentDark,
                    strokeWidth = 4.dp
                )
                Image(
                    res = R.drawable.ic_fearless_logo,
                    tint = colorAccentDark,
                    modifier = Modifier
                        .size(50.dp)
                        .align(Alignment.Center)
                )
            }
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

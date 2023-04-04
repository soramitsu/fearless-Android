package jp.co.soramitsu.soracard.impl.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.theme.soraRed

@Composable
fun SoraCard(
    onClick: (() -> Unit)
) {
    val noiseImage = ImageBitmap.imageResource(R.drawable.noise)
    val tiledNoise = remember(noiseImage) { ShaderBrush(ImageShader(noiseImage, TileMode.Repeated, TileMode.Repeated)) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(312.dp)
            .background(
                color = soraRed,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                brush = tiledNoise,
                shape = RoundedCornerShape(12.dp),
                alpha = 0.3f
            )
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.72f),
                        Color.White
                    )
                ),
                alpha = 0.2f
            )
    ) {
        Image(
            res = R.drawable.logo_soracard_text,
            modifier = Modifier
                .testTag("sora_logo_text")
                .padding(top = 8.dp, start = 12.dp)
                .align(Alignment.TopStart)
        )
        Image(
            res = R.drawable.logo_mc,
            modifier = Modifier
                .testTag("mc_logo")
                .padding(start = 10.dp, bottom = 6.dp)
                .align(Alignment.BottomStart)
        )
        Image(
            res = R.drawable.ic_sora_logo,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp, horizontal = 77.dp)
                .testTag("sora_logo")
                .align(Alignment.TopCenter),
            contentScale = ContentScale.FillWidth
        )
        Image(
            res = R.drawable.ic_paypass,
            modifier = Modifier
                .testTag("mc_logo")
                .padding(8.dp)
                .align(Alignment.CenterEnd)
        )
    }
}

@Preview
@Composable
private fun SoraCardPreview() {
    SoraCard {}
}

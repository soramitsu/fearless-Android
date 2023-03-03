package jp.co.soramitsu.common.compose.sora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.theme.soraRed

data class SoraCardItemCommonViewState(
    val kycStatus: String? = null,
    val visible: Boolean = false
)

@Composable
fun SoraCardItemCommon(
    state: SoraCardItemCommonViewState,
    onClose: (() -> Unit),
    onClick: (() -> Unit)? = null
) {
    val image = ImageBitmap.imageResource(R.drawable.noise)
    val tiledNoise = remember(image) { ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated)) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(
                color = soraRed,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                brush = tiledNoise,
                shape = RoundedCornerShape(12.dp),
                alpha = 0.3f
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
                .padding(top = 4.dp)
                .testTag("sora_logo")
                .align(Alignment.TopCenter)
        )
        Image(
            res = R.drawable.ic_paypass,
            modifier = Modifier
                .testTag("mc_logo")
                .padding(8.dp)
                .align(Alignment.BottomEnd)
        )

        if (state.kycStatus.isNullOrEmpty()) {
            Image(
                res = R.drawable.ic_close_16_white_circle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .wrapContentSize()
                    .clickable(onClick = onClose)
            )
        } else {
            H5(
                text = "** 2345",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 11.dp)
                    .wrapContentSize()
            )
        }

//        FilledButton()
    }
}

@Preview
@Composable
private fun SoraCardItemCommonPreview() {
    Column {
        val state = SoraCardItemCommonViewState(null, false)
        SoraCardItemCommon(state = state, {})
        val state2 = SoraCardItemCommonViewState("not null", false)
        SoraCardItemCommon(state = state2, {})
    }
}

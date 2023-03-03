package jp.co.soramitsu.soracard.impl.presentation

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.soraRed
import jp.co.soramitsu.soracard.api.presentation.models.SoraCardInfo
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size

data class SoraCardItemViewState(
    val kycStatus: String? = null,
    val soraCardInfo: SoraCardInfo? = null,
    val cardLastDigits: String? = null,
    val visible: Boolean = false
)

@Composable
fun SoraCardItem(
    state: SoraCardItemViewState?,
    onClose: (() -> Unit),
    onClick: (() -> Unit)
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
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.72f),
                        Color.White
                    )
                ),
                shape = RoundedCornerShape(12.dp),
                alpha = 0.2f
            )
            .clickable(onClick = onClick)
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

        if (state?.cardLastDigits.isNullOrEmpty()) {
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
                text = "** ${state?.cardLastDigits}",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 11.dp)
                    .wrapContentSize()
            )
        }

        if (state?.kycStatus != null) {
            FilledButton(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .padding(horizontal = 16.dp)
                    .testTag("sora_card_status")
                    .align(Alignment.BottomCenter),
                size = Size.ExtraSmall,
                order = Order.SECONDARY,
                onClick = onClick,
                text = state.kycStatus.uppercase(),
                elevation = 0.dp,
                maxLines = 1
            )
        }
    }
}

@Preview
@Composable
private fun SoraCardItemItemPreview() {
    Column {
        val state = SoraCardItemViewState(null, null, "3455", false)
        SoraCardItem(state = state, {}, {})
        MarginVertical(margin = 8.dp)
        val state2 = SoraCardItemViewState(null, null, null, false)
        SoraCardItem(state = state2, {}, {})
    }
}

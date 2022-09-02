package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.white

@Composable
fun NftStub(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Center
    ) {
        Column(horizontalAlignment = CenterHorizontally) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(100)
                    )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_screen_warning),
                    tint = white,
                    contentDescription = null,
                    modifier = Modifier
                        .testTag("nft_stub_icon")
                        .alpha(0.16f)
                        .padding(top = 10.dp)
                        .align(TopCenter)
                )
            }
            MarginVertical(margin = 16.dp)
            H3(text = stringResource(id = R.string.nft_stub_title))
            MarginVertical(margin = 16.dp)
            B0(
                text = stringResource(id = R.string.nft_stub_text),
                color = black2
            )
        }
    }
}

@Preview
@Composable
private fun AddressPreview() {
    FearlessTheme {
        Surface(Modifier.background(Color.Black)) {
            NftStub()
        }
    }
}

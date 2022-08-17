package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState

@Composable
fun AssetListItem(
    state: AssetListItemViewState,
    modifier: Modifier = Modifier
) {
    BackgroundCornered(modifier) {
        Row(
            Modifier
                .height(IntrinsicSize.Min)
                .padding(horizontal = 4.dp)
//            .blur(16.dp)
                .fillMaxWidth()

        ) {
            AsyncImage(
                model = getImageRequest(LocalContext.current, state.assetIconUrl),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .padding(start = 2.dp)
                    .alpha(0.5f)
                    .align(CenterVertically)
            )

            Divider(
                color = Color.White,
                modifier = Modifier
                    .height(64.dp)
                    .width(9.dp)
                    .padding(horizontal = 4.dp)
                    .alpha(0.16f)
                    .align(CenterVertically)
            )

            Column(
                Modifier
                    .padding(vertical = 8.dp)
                    .align(CenterVertically)
            ) {
                Row {
                    Text(
                        text = state.assetChainName.uppercase(),
                        style = MaterialTheme.customTypography.capsTitle2,
                        modifier = Modifier.alpha(0.64f)
                    )
                    Spacer(
                        modifier = Modifier
                            .height(1.dp)
                            .weight(1.0f)
                    )
                    AssetChainsBadge(urls = state.assetChainUrls)
                }
                Row {
                    Text(
                        text = state.assetSymbol,
                        style = MaterialTheme.customTypography.header3,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.weight(1.0f))
                    Text(
                        text = state.assetBalance,
                        style = MaterialTheme.customTypography.header3,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .padding(start = 4.dp)
                    )
                }
                Row {
                    Text(
                        text = state.assetTokenFiat,
                        style = MaterialTheme.customTypography.body1,
                        modifier = Modifier.alpha(0.64f)
                    )
                    Text(
                        text = state.assetTokenRate,
                        style = MaterialTheme.customTypography.body1.copy(
                            color = MaterialTheme.customColors.greenText
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(modifier = Modifier.weight(1.0f))
                    Text(
                        text = state.assetBalanceFiat,
                        style = MaterialTheme.customTypography.body1,
                        modifier = Modifier
                            .alpha(0.64f)
                            .padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

private fun getImageRequest(context: Context, url: String): ImageRequest {
    return ImageRequest.Builder(context)
        .data(url)
        .decoderFactory(SvgDecoder.Factory())
        .build()
}

@Preview
@Composable
fun PreviewAssetListItem() {
    val assetIconUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg"
    val assetChainName = "Karura"
    val assetSymbol = "KSM"
    val assetTokenFiat = "$73.22"
    val assetTokenRate = "+5.67%"
    val assetBalance = "444.3"
    val assetBalanceFiat = "$2345.32"
    val assetChainUrls = listOf(
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/kilt.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonbeam.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Statemine.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Rococo.svg"
    )

    val state = AssetListItemViewState(
        assetIconUrl = assetIconUrl,
        assetChainName = assetChainName,
        assetSymbol = assetSymbol,
        assetTokenFiat = assetTokenFiat,
        assetTokenRate = assetTokenRate,
        assetBalance = assetBalance,
        assetBalanceFiat = assetBalanceFiat,
        assetChainUrls = assetChainUrls
    )
    FearlessTheme {
        AssetListItem(state)
    }
}

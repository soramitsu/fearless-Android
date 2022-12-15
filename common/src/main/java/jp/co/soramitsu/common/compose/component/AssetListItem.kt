package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white16
import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState

@Composable
fun AssetListItem(
    state: AssetListItemViewState,
    modifier: Modifier = Modifier,
    onClick: (AssetListItemViewState) -> Unit
) {
    val hasIssues = !state.hasAccount || state.hasNetworkIssue
    val onClickHandler = remember { { onClick(state) } }
    BackgroundCornered(
        modifier = modifier
            .testTag("AssetListItem_${state.assetSymbol}_${state.assetChainName}")
            .clickable(onClick = onClickHandler)
    ) {
        val assetRateColor = if (state.assetTokenRate.orEmpty().startsWith("+")) {
            MaterialTheme.customColors.greenText
        } else {
            MaterialTheme.customColors.red
        }

        Row(
            Modifier
                .height(IntrinsicSize.Min)
                .padding(horizontal = 8.dp)
                .fillMaxWidth()

        ) {
            AsyncImage(
                model = getImageRequest(LocalContext.current, state.assetIconUrl),
                contentDescription = null,
                modifier = Modifier
                    .testTag("AssetListItem_${state.assetSymbol}_image")
                    .size(42.dp)
                    .padding(start = 4.dp)
                    .align(CenterVertically)
            )
            MarginHorizontal(margin = 8.dp)
            Divider(
                color = white16,
                modifier = Modifier
                    .height(64.dp)
                    .width(1.dp)
                    .align(CenterVertically)
            )
            MarginHorizontal(margin = 8.dp)
            Column(
                Modifier
                    .padding(vertical = 8.dp)
                    .align(CenterVertically)
            ) {
                Text(
                    text = state.assetChainName.uppercase(),
                    style = MaterialTheme.customTypography.capsTitle2,
                    modifier = Modifier
                        .alpha(0.64f)
                        .testTag("AssetListItem_${state.assetSymbol}_chain_name")
                )
                Text(
                    text = state.displayName.uppercase(),
                    style = MaterialTheme.customTypography.header3,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .testTag("AssetListItem_${state.assetSymbol}_symbol")
                )
                Row {
                    Text(
                        text = state.assetTokenFiat.orEmpty(),
                        style = MaterialTheme.customTypography.body1,
                        modifier = Modifier
                            .alpha(0.64f)
                            .testTag("AssetListItem_${state.assetSymbol}_change_fiat")
                    )
                    Text(
                        text = state.assetTokenRate.orEmpty(),
                        style = MaterialTheme.customTypography.body1.copy(
                            color = assetRateColor
                        ),
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .testTag("AssetListItem_${state.assetSymbol}_change_percent")
                    )
                }
            }
            Spacer(
                modifier = Modifier
                    .height(1.dp)
                    .weight(1.0f)
            )

            if (hasIssues) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_alert_16),
                    tint = alertYellow,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .size(24.dp)
                        .align(CenterVertically)
                        .testTag("AssetListItem_${state.assetSymbol}_alert_icon"),
                    contentDescription = null
                )
            } else {
                Column(
                    Modifier
                        .padding(vertical = 8.dp)
                        .align(CenterVertically)
                ) {
                    if (state.assetChainUrls.size > 1) {
                        AssetChainsBadge(
                            urls = state.assetChainUrls.values.toList(),
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("AssetListItem_${state.assetSymbol}_chains")
                        )
                    } else {
                        Box(modifier = Modifier.height(16.dp))
                    }
                    state.assetBalance?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.customTypography.header3,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .padding(start = 4.dp)
                                .align(Alignment.End)
                                .testTag("AssetListItem_${state.assetSymbol}_balance")
                        )
                    } ?: Shimmer(
                        Modifier
                            .size(height = 16.dp, width = 54.dp)
                            .align(Alignment.End)
                    )
                    Text(
                        text = state.assetBalanceFiat.orEmpty(),
                        style = MaterialTheme.customTypography.body1,
                        modifier = Modifier
                            .alpha(0.64f)
                            .padding(start = 4.dp)
                            .align(Alignment.End)
                            .testTag("AssetListItem_${state.assetSymbol}_balance_fiat")
                    )
                }
            }
        }
    }
}

@Composable
fun AssetListItemShimmer(
    state: AssetListItemShimmerViewState,
    modifier: Modifier = Modifier
) {
    BackgroundCornered(
        modifier = modifier
            .testTag("AssetListItem_shimmer")
    ) {
        Row(
            Modifier
                .height(IntrinsicSize.Min)
                .padding(horizontal = 8.dp)
                .fillMaxWidth()

        ) {
            AsyncImage(
                model = getImageRequest(LocalContext.current, state.assetIconUrl),
                contentDescription = null,
                modifier = Modifier
                    .testTag("AssetListItem_shimmer_image")
                    .size(42.dp)
                    .padding(start = 4.dp)
                    .align(CenterVertically)
                    .shimmer()
            )
            MarginHorizontal(margin = 8.dp)
            Divider(
                color = white16,
                modifier = Modifier
                    .height(64.dp)
                    .width(1.dp)
                    .align(CenterVertically)
            )
            MarginHorizontal(margin = 8.dp)
            Column(
                Modifier
                    .padding(vertical = 8.dp)
                    .align(CenterVertically)
            ) {
                Shimmer(
                    Modifier
                        .height(11.dp)
                        .width(95.dp)
                )
                MarginVertical(margin = 8.dp)
                Shimmer(
                    Modifier
                        .height(16.dp)
                        .width(43.dp)
                )
                MarginVertical(margin = 11.dp)
                Row {
                    Shimmer(
                        Modifier
                            .height(12.dp)
                            .width(51.dp)
                    )
                    Shimmer(
                        Modifier
                            .height(12.dp)
                            .width(53.dp)
                            .padding(start = 4.dp)
                    )
                }
            }
            Spacer(
                modifier = Modifier
                    .height(1.dp)
                    .weight(1.0f)
            )

            Column(
                Modifier
                    .padding(vertical = 8.dp)
                    .align(CenterVertically)
            ) {
                AssetChainsBadge(
                    urls = state.assetChainUrls,
                    modifier = Modifier
                        .align(Alignment.End)
                        .shimmer()
                        .testTag("AssetListItem_shimmer_chains")
                )
                MarginVertical(margin = 8.dp)
                Shimmer(
                    Modifier
                        .size(height = 16.dp, width = 54.dp)
                        .align(Alignment.End)
                )
                MarginVertical(margin = 11.dp)
                Shimmer(
                    Modifier
                        .size(height = 12.dp, width = 69.dp)
                        .align(Alignment.End)
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewAssetListItem() {
    val assetIconUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg"
    val assetChainName = "Karura"
    val assetSymbol = "KSM"
    val assetTokenFiat = "$73.22"
    val assetTokenRate = "+5.67%"
    val assetBalance = "444.3"
    val assetBalanceFiat = "$2345.32"
    val assetChainUrlsMap = mapOf(
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/kilt.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonbeam.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Statemine.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Rococo.svg"
    )

    val state = AssetListItemViewState(
        assetIconUrl = assetIconUrl,
        assetChainName = assetChainName,
        assetSymbol = assetSymbol,
        assetTokenFiat = assetTokenFiat,
        assetTokenRate = assetTokenRate,
        assetBalance = assetBalance,
        assetBalanceFiat = assetBalanceFiat,
        assetChainUrls = assetChainUrlsMap,
        chainId = "",
        chainAssetId = "",
        isSupported = true,
        isHidden = false,
        displayName = assetSymbol,
        hasAccount = true,
        priceId = null,
        hasNetworkIssue = false
    )
    FearlessTheme {
        Box(modifier = Modifier.background(Color.Black)) {
            Column {
                AssetListItem(state) {}
                AssetListItem(state.copy(hasAccount = false)) {}
                AssetListItemShimmer(
                    state = AssetListItemShimmerViewState(assetIconUrl, assetChainUrlsMap.values.toList())
                )
            }
        }
    }
}

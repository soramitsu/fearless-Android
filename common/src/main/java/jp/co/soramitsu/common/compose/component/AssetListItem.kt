package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.bold
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white16
import jp.co.soramitsu.common.compose.theme.white64
import jp.co.soramitsu.common.compose.viewstate.AssetListItemShimmerViewState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.oauth.base.sdk.contract.IbanInfo
import jp.co.soramitsu.oauth.base.sdk.contract.IbanStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetListItem(
    state: AssetListItemViewState,
    modifier: Modifier = Modifier,
    onClick: (AssetListItemViewState) -> Unit
) {
    BackgroundCornered(
        modifier = modifier
            .testTag("AssetListItem_${state.assetSymbol}_${state.assetName}")
            .clickable(onClick = { onClick(state) })
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
            Box(
                Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .padding(vertical = 8.dp)
                        .align(CenterStart)
                ) {
                    Row {
                        Text(
                            text = state.assetName.uppercase(),
                            style = MaterialTheme.customTypography.capsTitle2,
                            modifier = Modifier
                                .alpha(0.64f)
                                .testTag("AssetListItem_${state.assetSymbol}_chain_name")
                        )
                        if (state.isTestnet) {
                            MarginHorizontal(margin = 4.dp)
                            TestnetBadge()
                        }
                        Spacer(
                            modifier = Modifier
                                .height(1.dp)
                                .weight(1.0f)
                        )
                        AssetChainsBadge(
                            urls = state.assetChainUrls.values.toList(),
                            modifier = Modifier
                                .testTag("AssetListItem_${state.assetSymbol}_chains")
                        )
                    }
                    Row {
                        Text(
                            text = state.assetSymbol.uppercase(),
                            style = MaterialTheme.customTypography.header3,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .align(CenterVertically)
                                .testTag("AssetListItem_${state.assetSymbol}_symbol")
                        )
                        Spacer(
                            modifier = Modifier
                                .height(1.dp)
                                .weight(1.0f)
                        )
                        if (state.assetTransferableBalance == null) {
                            Shimmer(
                                Modifier
                                    .padding(top = 8.dp, bottom = 4.dp)
                                    .size(height = 16.dp, width = 54.dp)
                            )
                        } else {
                            Text(
                                text = state.assetTransferableBalance,
                                style = MaterialTheme.customTypography.header3.copy(textAlign = TextAlign.End),
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .padding(start = 4.dp)
                                    .testTag("AssetListItem_${state.assetSymbol}_transferable")
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .weight(1f)
                        ) {
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
                        Text(
                            text = state.assetTransferableBalanceFiat.orEmpty(),
                            style = MaterialTheme.customTypography.body1.copy(textAlign = TextAlign.End),
                            modifier = Modifier
                                .wrapContentWidth()
                                .alpha(0.64f)
                                .padding(start = 4.dp)
                                .testTag("AssetListItem_${state.assetSymbol}_transferable_fiat")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TestnetBadge() {
    Card(backgroundColor = white16) {
        Row(
            modifier = Modifier
                .padding(bottom = 2.dp, start = 2.dp, end = 4.dp),
            verticalAlignment = CenterVertically
        ) {
            Icon(
                modifier = Modifier
                    .width(16.dp)
                    .padding(top = 1.dp),
                painter = painterResource(R.drawable.ic_token_testnet),
                tint = white64,
                contentDescription = null
            )
            MarginHorizontal(margin = 4.dp)
            Text(
                text = stringResource(id = R.string.label_testnet).uppercase(),
                style = MaterialTheme.customTypography.body3.bold().copy(color = white64)
            )
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
    val assetIconUrl =
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg"
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
        index = 0,
        assetIconUrl = assetIconUrl,
        assetName = "Karura asset",
        assetChainName = "Karura",
        assetSymbol = "KSM",
        assetTokenFiat = "$73.22000003242342",
        assetTokenRate = "+5.67%",
        assetTransferableBalance = "444.30",
        assetTransferableBalanceFiat = "$2345.3233434334",
        assetChainUrls = assetChainUrlsMap,
        chainId = "",
        chainAssetId = "",
        isSupported = true,
        isHidden = false,
        isTestnet = false
    )
    FearlessAppTheme {
        Box {
            Column {
                AssetListItem(state) {}
                MarginVertical(margin = 8.dp)
                AssetListItem(
                    state.copy(
                        isTestnet = true,
                        assetTransferableBalance = "123,456,123,456,123,456,789,456,789.01234"
                    )
                ) {}
                MarginVertical(margin = 8.dp)
                AssetListItemShimmer(
                    state = AssetListItemShimmerViewState(
                        assetIconUrl,
                        assetChainUrlsMap.values.toList()
                    )
                )
                MarginVertical(margin = 8.dp)
                SoraCardFiatCard(
                    state = previewSoraCardItemViewState,
                    modifier = Modifier,
                    onClick = {},
                )
            }
        }
    }
}

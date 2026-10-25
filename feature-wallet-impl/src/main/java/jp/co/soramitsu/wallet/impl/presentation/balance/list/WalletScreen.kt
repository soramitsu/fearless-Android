package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalance
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.BannerJoinSubstrateEvm
import jp.co.soramitsu.common.compose.component.BannerJoinTon
import jp.co.soramitsu.common.compose.component.BannerWalletRecovery
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MultiToggleButton
import jp.co.soramitsu.common.compose.component.MultiToggleButtonState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.white64
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.utils.rememberForeverLazyListState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.NFTScreen
import jp.co.soramitsu.wallet.impl.presentation.common.AssetsList
import jp.co.soramitsu.wallet.impl.presentation.common.AssetsListInterface
import jp.co.soramitsu.wallet.impl.presentation.common.NetworkIssue

@Stable
interface WalletScreenInterface : AssetsListInterface {
    fun onAddressClick()
    fun onBalanceClicked()
    fun onBackupClicked()
    fun onBackupCloseClick()
    fun onJoinSubOrEvmClicked()
    fun onJoinSubOrEvmCloseClick()
    fun onJoinTonClicked()
    fun onJoinTonCloseClick()
    fun assetTypeChanged(type: AssetType)
    fun onRefresh()
    fun onManageAssetClick()
    fun onHiddenFundsReviewClick()
    fun onHiddenFundsKeepHiddenClick()
    fun onRetry()
    fun onPortfolioLensChanged(lens: PortfolioLens) {}
    fun onPortfolioGroupToggle(id: String) {}
    fun onPortfolioNetworkToggle(id: String) {}
    fun onPortfolioZeroToggle(id: String?) {}
    fun onPortfolioValueNoticeDismiss() {}
    fun onPortfolioMemberAction(walletId: Long, chainId: String, assetId: String, action: ActionItemType) {}
    fun onPortfolioHideGroup(walletId: Long, groupId: String) {}
    fun onWalletRecoveryClick()
}

@Composable
fun WalletScreen(
    data: WalletState,
    callback: WalletScreenInterface
) {
    val viewOnly by WalletViewOnlyAccess.enabled.collectAsState()
    val listState = rememberForeverLazyListState("wallet_screen")

    val scale = remember { Animatable(initialValue = 1f) }

    LaunchedEffect(data.scrollToTopEvent) {
        data.scrollToTopEvent?.getContentIfNotHandled()?.let {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(data.scrollToBottomEvent) {
        data.scrollToBottomEvent?.getContentIfNotHandled()?.let {
            if (data.assetsState is WalletAssetsState.Assets && data.assetsState.assets is AssetsLoadingState.Loaded) {

                val lastItemIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                listState.animateScrollToItem(lastItemIndex)

                scale.animateTo(
                    targetValue = 1.2f,
                    animationSpec = tween(durationMillis = 600)
                )
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 600)
                )
            }
        }
    }


    val largeText = LocalDensity.current.fontScale > 1.3f
    val scrollHeaderWithAssets = largeText && data.assetsState is WalletAssetsState.Assets
    val walletHeader: @Composable () -> Unit = {
        Column {
            MarginVertical(margin = 16.dp)
            AssetBalance(
                state = data.balance,
                onAddressClick = callback::onAddressClick,
                onBalanceClick = callback::onBalanceClicked
            )

            MarginVertical(margin = 16.dp)
            when {
                viewOnly -> BannerWalletRecovery(onClick = callback::onWalletRecoveryClick, viewOnly = true)
                data.isRecoveryRequired -> BannerWalletRecovery(onClick = callback::onWalletRecoveryClick)
                !data.isBackedUp -> BackgroundCornered(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        B1(text = stringResource(R.string.ux_backup_description))
                        MarginVertical(8.dp)
                        GrayButton(
                            text = stringResource(R.string.ux_backup_status),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = callback::onBackupClicked
                        )
                    }
                }
            }
            MarginVertical(8.dp)
            AnimatedVisibility(
                visible = data.showCurrenciesOrNftSelector,
                enter = fadeIn(),
                exit = fadeOut()
            ) {

                MultiToggleButton(
                    state = data.multiToggleButtonState,
                    onToggleChange = callback::assetTypeChanged,
                    stacked = largeText
                )
            }

        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (!scrollHeaderWithAssets && !(largeText && data.assetsState is WalletAssetsState.NetworkIssue)) walletHeader()
        when (data.assetsState) {
            is WalletAssetsState.NftAssets -> {
                NFTScreen(collectionsScreen = data.assetsState.collectionScreenModel)
            }

            is WalletAssetsState.Assets -> {
                val loaded = data.assetsState.assets as? AssetsLoadingState.Loaded
                val header: @Composable () -> Unit = {
                    if (scrollHeaderWithAssets) walletHeader()
                    if (loaded != null && loaded.selectedNetwork == null) {
                        MultiToggleButton(MultiToggleButtonState(loaded.portfolioUi.lens, PortfolioLens.entries),
                            callback::onPortfolioLensChanged, stacked = largeText)
                        MarginVertical(8.dp)
                    }
                    if (loaded != null && !loaded.portfolioUi.valueNoticeAcknowledged) {
                        BackgroundCornered {
                            Column(Modifier.padding(12.dp)) {
                                B2(text = stringResource(R.string.portfolio_value_notice), color = white64)
                                GrayButton(stringResource(R.string.common_got_it), modifier = Modifier.fillMaxWidth(),
                                    onClick = callback::onPortfolioValueNoticeDismiss)
                            }
                        }
                        MarginVertical(8.dp)
                    }
                    val hiddenFundedNoticeCount = loaded?.hiddenFundedNoticeCount ?: 0
                    if (hiddenFundedNoticeCount > 0) {
                        HiddenFundsNotice(
                            count = hiddenFundedNoticeCount,
                            stacked = largeText,
                            onReviewClick = callback::onHiddenFundsReviewClick,
                            onKeepHiddenClick = callback::onHiddenFundsKeepHiddenClick
                        )
                    }
                }
                val footer: @Composable () -> Unit =
                    {
                        PortfolioExclusionsNote(
                            exclusions = loaded?.exclusions ?: PortfolioExclusions(),
                            onManageHiddenClick = callback::onHiddenFundsReviewClick
                        )
                        WalletScreenFooter(
                            scale = scale.value,
                            onManageAssetsClick = callback::onManageAssetClick
                        )
                        MarginVertical(16.dp)
                        Banners(data, callback)
                    }
                if (loaded != null && loaded.walletId != null) PortfolioList(loaded, data.assetsState.isHideVisible,
                    callback, listState, header, footer)
                else AssetsList(
                    data = data.assetsState,
                    callback = callback,
                    header = header,
                    listState = listState,
                    footer = footer
                )
            }

            is WalletAssetsState.NetworkIssue -> {
                if (largeText) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        walletHeader()
                        NetworkIssue(data.assetsState.retryButtonLoading, callback::onRetry)
                    }
                } else {
                    NetworkIssue(data.assetsState.retryButtonLoading, callback::onRetry)
                }
            }
        }
    }
}

@Composable
private fun Banners(
    data: WalletState,
    callback: WalletScreenInterface
) {
    // Optional network discovery stays below assets, separate from wallet protection.
    if (!data.hasSubOrEvmAccounts) {
        BannerJoinSubstrateEvm(
            onClick = callback::onJoinSubOrEvmClicked,
            onCloseClick = callback::onJoinSubOrEvmCloseClick
        )
        MarginVertical(8.dp)
    }
    if (!data.hasTonAccounts) {
        BannerJoinTon(
            onClick = callback::onJoinTonClicked,
            onCloseClick = callback::onJoinTonCloseClick
        )
        MarginVertical(8.dp)
    }
}

@Composable
fun WalletScreenWithRefresh(
    data: WalletState,
    callback: WalletScreenInterface
) {
    PullRefreshBox(
        onRefresh = callback::onRefresh
    ) {
        WalletScreen(data, callback)
    }
}

/**
 * Shown above the list while a hidden asset the user has not been told about holds funds: an
 * earlier version may have hidden it, and a deposit into a hidden asset is otherwise invisible.
 */
@Composable
fun HiddenFundsNotice(
    count: Int,
    stacked: Boolean,
    onReviewClick: () -> Unit,
    onKeepHiddenClick: () -> Unit
) {
    BackgroundCornered(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_hidden_funds_notice")
    ) {
        Column(Modifier.padding(16.dp)) {
            B1(
                text = pluralStringResource(R.plurals.portfolio_hidden_assets_hold_funds, count, count),
                fontWeight = FontWeight.W600,
                modifier = Modifier.semantics { heading() }
            )
            MarginVertical(4.dp)
            B2(text = stringResource(R.string.portfolio_hidden_assets_hold_funds_description), color = white64)
            MarginVertical(12.dp)
            val review: @Composable (Modifier) -> Unit = { modifier ->
                GrayButton(
                    text = stringResource(R.string.portfolio_hidden_assets_review),
                    modifier = modifier
                        .height(48.dp)
                        .testTag("portfolio_hidden_funds_review"),
                    onClick = onReviewClick
                )
            }
            val keepHidden: @Composable (Modifier) -> Unit = { modifier ->
                GrayButton(
                    text = stringResource(R.string.portfolio_hidden_assets_keep_hidden),
                    modifier = modifier
                        .height(48.dp)
                        .testTag("portfolio_hidden_funds_keep_hidden"),
                    onClick = onKeepHiddenClick
                )
            }
            if (stacked) {
                review(Modifier.fillMaxWidth())
                MarginVertical(8.dp)
                keepHidden(Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    review(Modifier.weight(1f))
                    keepHidden(Modifier.weight(1f))
                }
            }
        }
    }
}

/** What the total leaves out, under the list: hidden assets, assets without price, pool stakes. */
@Composable
fun PortfolioExclusionsNote(
    exclusions: PortfolioExclusions,
    onManageHiddenClick: () -> Unit
) {
    if (exclusions.isEmpty) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_exclusions")
    ) {
        if (exclusions.hiddenAssetCount > 0 || exclusions.hiddenFundedCount > 0) {
            B2(
                text = stringResource(R.string.portfolio_hidden_manage,
                    maxOf(exclusions.hiddenAssetCount, exclusions.hiddenFundedCount), exclusions.hiddenFiat.orEmpty()),
                color = white64,
                modifier = Modifier
                    .testTag("portfolio_hidden_not_included")
                    .clickable(role = Role.Button, onClick = onManageHiddenClick)
                    .padding(vertical = 8.dp)
            )
        }
        if (exclusions.unpricedFundedCount > 0) {
            B2(
                text = pluralStringResource(
                    R.plurals.portfolio_assets_without_price_not_included,
                    exclusions.unpricedFundedCount,
                    exclusions.unpricedFundedCount
                ),
                color = white64,
                modifier = Modifier
                    .testTag("portfolio_unpriced_not_included")
                    .padding(vertical = 4.dp)
            )
        }
        if (exclusions.poolStakesExcluded) {
            B2(
                text = stringResource(R.string.portfolio_pool_stakes_not_included),
                color = white64,
                modifier = Modifier
                    .testTag("portfolio_pool_stakes_not_included")
                    .padding(vertical = 4.dp)
            )
        }
    }
    MarginVertical(8.dp)
}

@Composable
fun WalletScreenFooter(
    scale: Float,
    onManageAssetsClick: () -> Unit
) {
    GrayButton(
        text = stringResource(id = R.string.wallet_manage_assets),
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = 300,
                    easing = LinearOutSlowInEasing
                )
            )
            .height(48.dp),
        onClick = onManageAssetsClick
    )
}

@Preview
@Composable
private fun PreviewWalletScreen() {
    @OptIn(ExperimentalMaterialApi::class)
    val emptyCallback = object : WalletScreenInterface {
        override fun onAddressClick() {}
        override fun onBalanceClicked() {}
        override fun onBackupClicked() {}
        override fun onBackupCloseClick() {}
        override fun onWalletRecoveryClick() {}
        override fun onJoinSubOrEvmClicked() {}
        override fun onJoinSubOrEvmCloseClick() {}
        override fun onJoinTonClicked() {}
        override fun onJoinTonCloseClick() {}
        override fun assetTypeChanged(type: AssetType) {}
        override fun assetClicked(state: AssetListItemViewState) {}
        override fun actionItemClicked(
            actionType: ActionItemType,
            chainId: ChainId,
            chainAssetId: String,
            swipeableState: SwipeableState<SwipeState>
        ) {
        }

        override fun onRefresh() {}
        override fun onHiddenFundsReviewClick() {}
        override fun onHiddenFundsKeepHiddenClick() {}
        override fun onManageAssetClick() {}
        override fun onRetry() {}
    }

    val element = AssetListItemViewState(
        index = 0,
        assetIconUrl = "",
        assetChainName = "Chain",
        assetSymbol = "SMB",
        assetName = "Sora Asset",
        assetTokenFiat = null,
        assetTokenRate = null,
        assetTransferableBalance = null,
        assetTransferableBalanceFiat = null,
        assetChainUrls = emptyMap(),
        chainId = "",
        chainAssetId = "",
        isSupported = true,
        isHidden = false,
        isTestnet = false
    )
    val assets: List<AssetListItemViewState> = listOf(
        element, element, element.copy(isHidden = true)
    ).mapIndexed { index, assetListItemViewState ->
        assetListItemViewState.copy(index = index)
    }

    FearlessAppTheme {
        Column {
            WalletScreen(
                data = WalletState(
                    multiToggleButtonState = MultiToggleButtonState(
                        AssetType.Currencies,
                        listOf(AssetType.Currencies, AssetType.NFTs)
                    ),
                    assetsState = WalletAssetsState.Assets(AssetsLoadingState.Loaded(assets), isHideVisible = true),
                    balance = AssetBalanceViewState(
                        "TRANSFERABLE BALANCE",
                        "ADDRESS",
                        true,
                        ChangeBalanceViewState("+100%", "+50$")
                    ),
                    hasNetworkIssues = true,
                    isBackedUp = false,
                    isRecoveryRequired = false,
                    hasTonAccounts = false,
                    hasSubOrEvmAccounts = false,
                    showCurrenciesOrNftSelector = false,
                    scrollToTopEvent = null,
                    scrollToBottomEvent = null
                ),
                callback = emptyCallback
            )
        }
    }
}

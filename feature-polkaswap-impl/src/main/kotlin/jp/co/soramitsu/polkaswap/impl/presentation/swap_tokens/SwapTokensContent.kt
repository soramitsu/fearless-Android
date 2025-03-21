package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.BannerDemeter
import jp.co.soramitsu.common.compose.component.BannerLiquidityPools
import jp.co.soramitsu.common.compose.component.BannerPageIndicator
import jp.co.soramitsu.common.compose.component.FeeInfo
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.NavigationIconButton
import jp.co.soramitsu.common.compose.component.Notification
import jp.co.soramitsu.common.compose.component.NotificationState
import jp.co.soramitsu.common.compose.component.QuickAmountInput
import jp.co.soramitsu.common.compose.component.QuickInput
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.dataOrNull
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import kotlinx.coroutines.delay

data class SwapTokensContentViewState(
    val fromAmountInputViewState: AmountInputViewState,
    val toAmountInputViewState: AmountInputViewState,
    val selectedMarket: Market,
    val swapDetailsViewState: SwapDetailsViewState?,
    val isLoading: Boolean,
    val networkFeeViewState: LoadingState<out SwapDetailsViewState.NetworkFee?>,
    val showLiquidityBanner: Boolean,
    val hasReadDisclaimer: Boolean,
    val isSoftKeyboardOpen: Boolean
) {
    companion object {

        fun default(resourceManager: ResourceManager): SwapTokensContentViewState {
            return SwapTokensContentViewState(
                fromAmountInputViewState = AmountInputViewState.defaultObj.copy(totalBalance = resourceManager.getString(R.string.common_available_format, "0")),
                toAmountInputViewState = AmountInputViewState.defaultObj.copy(totalBalance = resourceManager.getString(R.string.common_balance_format, "0")),
                selectedMarket = Market.SMART,
                swapDetailsViewState = null,
                isLoading = false,
                networkFeeViewState = LoadingState.Loaded(null),
                showLiquidityBanner = true,
                hasReadDisclaimer = false,
                isSoftKeyboardOpen = false
            )
        }
    }
}

interface SwapTokensCallbacks {

    fun onChangeTokensClick()

    fun onBackClick()

    fun onPreviewClick()

    fun onFromAmountChange(amount: BigDecimal)

    fun onToAmountChange(amount: BigDecimal)

    fun onMarketSettingsClick()

    fun onFromTokenSelect()

    fun onToTokenSelect()

    fun onFromAmountFocusChange(isFocused: Boolean)

    fun onToAmountFocusChange(isFocused: Boolean)

    fun minMaxToolTopClick()

    fun networkFeeTooltipClick()

    fun onQuickAmountInput(value: Double)

    fun onDisclaimerClick()

    fun onPoolsClick()

    fun onLiquidityBannerClose()
}

@Composable
fun SwapTokensContent(
    state: SwapTokensContentViewState,
    callbacks: SwapTokensCallbacks,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val runCallback: (() -> Unit) -> Unit = { block ->
        keyboardController?.hide()
        block()
    }

    val fromFocusRequester = remember { FocusRequester() }
    val toFocusRequester = remember { FocusRequester() }

    fun onChangeTokensClick() {
        when {
            state.fromAmountInputViewState.isFocused -> {
                toFocusRequester.requestFocus()
            }
            state.toAmountInputViewState.isFocused -> {
                fromFocusRequester.requestFocus()
            }
        }

        callbacks.onChangeTokensClick()
    }

    val isFromFocused = state.fromAmountInputViewState.isFocused && !state.fromAmountInputViewState.tokenName.isNullOrEmpty()
    val showQuickInput = isFromFocused && state.isSoftKeyboardOpen

    Column(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationIconButton(
                modifier = Modifier.padding(start = 16.dp),
                onNavigationClick = callbacks::onBackClick
            )

            Image(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(
                        width = 100.dp,
                        height = 28.dp
                    ),
                painter = painterResource(R.drawable.logo_polkaswap_big),
                contentDescription = null
            )
            Spacer(modifier = Modifier.weight(1f))

            MarketLabel(
                modifier = Modifier.padding(end = 16.dp),
                market = state.selectedMarket,
                onClick = { runCallback(callbacks::onMarketSettingsClick) }
            )
        }
        FullScreenLoading(isLoading = state.isLoading) {
            Column {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            AmountInput(
                                state = state.fromAmountInputViewState,
                                borderColorFocused = colorAccentDark,
                                onInput = callbacks::onFromAmountChange,
                                onInputFocusChange = callbacks::onFromAmountFocusChange,
                                onTokenClick = { runCallback(callbacks::onFromTokenSelect) },
                                focusRequester = fromFocusRequester
                            )

                            MarginVertical(margin = 8.dp)

                            AmountInput(
                                state = state.toAmountInputViewState,
                                borderColorFocused = colorAccentDark,
                                onInput = callbacks::onToAmountChange,
                                onInputFocusChange = callbacks::onToAmountFocusChange,
                                onTokenClick = { runCallback(callbacks::onToTokenSelect) },
                                focusRequester = toFocusRequester
                            )
                        }

                        Icon(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(grayButtonBackground)
                                .border(width = 1.dp, color = white08, shape = CircleShape)
                                .clickable { runCallback(::onChangeTokensClick) }
                                .padding(8.dp),
                            painter = painterResource(R.drawable.ic_exchange),
                            contentDescription = null,
                            tint = colorAccentDark
                        )
                    }

                    if (state.swapDetailsViewState != null) {
                        TransactionDescription(
                            swapDetailsViewState = state.swapDetailsViewState,
                            networkFeeViewState = state.networkFeeViewState,
                            callbacks = callbacks
                        )
                    }
                    if (state.showLiquidityBanner && state.isSoftKeyboardOpen.not()) {
                        Spacer(modifier = Modifier.weight(1f))

                        Banners(
                            showLiquidity = state.showLiquidityBanner,
                            showDemeter = false,
                            callback = callbacks
                        )
                    }                }
                if (state.hasReadDisclaimer.not()) {
                    Box(modifier = modifier.padding(horizontal = 16.dp)) {
                        Notification(
                            state = NotificationState(
                                iconRes = R.drawable.ic_warning_filled,
                                title = stringResource(R.string.common_disclaimer).uppercase(),
                                value = stringResource(id = R.string.polkaswap_disclaimer_message),
                                buttonText = stringResource(R.string.common_read),
                                color = warningOrange
                            ),
                            onAction = callbacks::onDisclaimerClick
                        )
                    }
                    MarginVertical(margin = 16.dp)
                }

                AccentButton(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    text = stringResource(R.string.common_preview),
                    enabled = state.swapDetailsViewState != null,
                    onClick = { runCallback(callbacks::onPreviewClick) }
                )
                MarginVertical(margin = 8.dp)

                if (showQuickInput) {
                    QuickInput(
                        values = QuickAmountInput.entries.toTypedArray(),
                        onQuickAmountInput = {
                            keyboardController?.hide()
                            callbacks.onQuickAmountInput(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionDescription(
    swapDetailsViewState: SwapDetailsViewState,
    networkFeeViewState: LoadingState<out SwapDetailsViewState.NetworkFee?>,
    modifier: Modifier = Modifier,
    callbacks: SwapTokensCallbacks
) {
    Column(
        modifier = modifier
    ) {
        FeeInfo(
            modifier = Modifier.padding(top = 8.dp),
            state = FeeInfoViewState(
                caption = swapDetailsViewState.minmaxTitle,
                feeAmount = swapDetailsViewState.toTokenMinReceived,
                feeAmountFiat = swapDetailsViewState.toFiatMinReceived,
                tooltip = true
            ),
            tooltipClick = callbacks::minMaxToolTopClick
        )

        FeeInfo(
            state = FeeInfoViewState(
                caption = stringResource(R.string.common_route),
                feeAmount = swapDetailsViewState.route,
                feeAmountFiat = null
            )
        )

        FeeInfo(
            state = FeeInfoViewState(
                caption = "${swapDetailsViewState.fromTokenName} / ${swapDetailsViewState.toTokenName}",
                feeAmount = swapDetailsViewState.fromTokenOnToToken,
                feeAmountFiat = null
            )
        )

        FeeInfo(
            state = FeeInfoViewState(
                caption = "${swapDetailsViewState.toTokenName} / ${swapDetailsViewState.fromTokenName}",
                feeAmount = swapDetailsViewState.toTokenOnFromToken,
                feeAmountFiat = null
            )
        )

        when {
            networkFeeViewState is LoadingState.Loading -> FeeInfo(
                state = FeeInfoViewState(
                    caption = stringResource(R.string.common_network_fee),
                    feeAmount = null,
                    feeAmountFiat = null,
                    tooltip = true
                ),
                tooltipClick = callbacks::networkFeeTooltipClick
            )
            networkFeeViewState is LoadingState.Loaded && networkFeeViewState.dataOrNull() != null -> FeeInfo(
                state = FeeInfoViewState(
                    caption = stringResource(R.string.common_network_fee),
                    feeAmount = networkFeeViewState.dataOrNull()?.tokenAmount,
                    feeAmountFiat = networkFeeViewState.dataOrNull()?.fiatAmount,
                    tooltip = true
                ),
                tooltipClick = callbacks::networkFeeTooltipClick
            )
        }
    }
}

@Composable
private fun MarketLabel(
    market: Market,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(black05)
            .clickable { onClick.invoke() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.padding(
                start = 8.dp,
                top = 4.dp,
                bottom = 4.dp,
                end = 4.dp
            ),
            text = stringResource(R.string.polkaswap_market),
            style = MaterialTheme.customTypography.body1
        )

        Text(
            text = market.marketName,
            style = MaterialTheme.customTypography.header5
        )

        Icon(
            modifier = Modifier
                .padding(start = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.customColors.white08)
                .size(32.dp)
                .padding(4.dp),
            painter = painterResource(R.drawable.ic_settings_swap),
            contentDescription = null,
            tint = MaterialTheme.customColors.white
        )
    }
}

@Composable
private fun Banners(
    showLiquidity: Boolean,
    showDemeter: Boolean,
    callback: SwapTokensCallbacks,
    autoPlay: Boolean = true
) {
    val bannerLiquidityPools: @Composable (() -> Unit)? = if (showLiquidity) {
        {
            BannerLiquidityPools(
                onShowMoreClick = callback::onPoolsClick,
                onCloseClick = callback::onLiquidityBannerClose
            )
        }
    } else null

    val bannerDemeter: @Composable (() -> Unit)? = if (showDemeter) {
        {
            BannerDemeter(
                onShowMoreClick = {},
                onCloseClick = {}
            )
        }
    } else null

    val banners = listOfNotNull(bannerLiquidityPools, bannerDemeter)
    val bannersCount = banners.size
    val pagerState = rememberPagerState { bannersCount }

    if (bannersCount > 1) {
        // Auto play
        LaunchedEffect(key1 = autoPlay) {
            if (autoPlay) {
                while (true) {
                    delay(5000L)
                    with(pagerState) {
                        animateScrollToPage(
                            page = (currentPage + 1) % bannersCount,
                            animationSpec = tween(
                                durationMillis = 500,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                }
            }
        }
    }
    HorizontalPager(
        modifier = Modifier.fillMaxWidth(),
        state = pagerState,
        pageSpacing = 8.dp,
        pageContent = { page ->
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                banners[page].invoke()
            }
        }
    )
    MarginVertical(margin = 8.dp)

    if (bannersCount > 1) {
        BannerPageIndicator(bannersCount, pagerState)
        MarginVertical(margin = 8.dp)
    }
    MarginVertical(margin = 8.dp)
}

@Preview
@Composable
fun SwapTokensContentPreview() {
    FearlessAppTheme {
        val amountInputViewState = AmountInputViewState(
            tokenAmount = BigDecimal.ZERO,
            title = "title",
            tokenName = "tokenName",
            fiatAmount = "fialtAmount",
            totalBalance = "totalBalance"
        )
        val state = SwapTokensContentViewState(
            fromAmountInputViewState = amountInputViewState.copy(title = "From title"),
            toAmountInputViewState = amountInputViewState.copy(title = "To title"),
            selectedMarket = Market.SMART,
            swapDetailsViewState = null,
            isLoading = false,
            networkFeeViewState = LoadingState.Loading(),
            showLiquidityBanner = true,
            hasReadDisclaimer = false,
            isSoftKeyboardOpen = false
        )
        val callbacks = object : SwapTokensCallbacks {
            override fun onChangeTokensClick() {}
            override fun onBackClick() {}
            override fun onPreviewClick() {}
            override fun onFromAmountChange(amount: BigDecimal) {}
            override fun onToAmountChange(amount: BigDecimal) {}
            override fun onMarketSettingsClick() {}
            override fun onFromTokenSelect() {}
            override fun onToTokenSelect() {}
            override fun onFromAmountFocusChange(isFocused: Boolean) {}
            override fun onToAmountFocusChange(isFocused: Boolean) {}
            override fun minMaxToolTopClick() {}
            override fun networkFeeTooltipClick() {}
            override fun onQuickAmountInput(value: Double) {}
            override fun onDisclaimerClick() {}
            override fun onPoolsClick() {}
            override fun onLiquidityBannerClose() {}
        }

        SwapTokensContent(
            state = state,
            callbacks = callbacks,
        )
    }
}
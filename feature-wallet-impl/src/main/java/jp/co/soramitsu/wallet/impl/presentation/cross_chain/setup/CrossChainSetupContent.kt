package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AddressInput
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.Badge
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfo
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.QuickAmountInput
import jp.co.soramitsu.common.compose.component.QuickInput
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SelectorWithBorder
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WarningInfo
import jp.co.soramitsu.common.compose.component.WarningInfoState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import java.math.BigDecimal

data class CrossChainSetupViewState(
    val toolbarState: ToolbarViewState,
    val addressInputState: AddressInputState,
    val amountInputState: AmountInputViewState,
    val originChainSelectorState: SelectorState,
    val destinationChainSelectorState: SelectorState,
    val originFeeInfoState: FeeInfoViewState,
    val destinationFeeInfoState: FeeInfoViewState?,
    val warningInfoState: WarningInfoState?,
    val buttonState: ButtonViewState,
    val walletIcon: Drawable?,
    val isSoftKeyboardOpen: Boolean,
    val routeCapabilityMessage: String,
    val routeCapability: CrossChainRouteCapability?,
    val routeInventory: List<CrossChainRouteInventoryItem>
)

interface CrossChainSetupScreenInterface {
    fun onNavigationClick()
    fun onAddressInput(input: String)
    fun onAddressInputClear()
    fun onAmountInput(input: BigDecimal?)
    fun onOriginChainClick()
    fun onDestinationChainClick()
    fun onAssetClick()
    fun onNextClick()
    fun onQrClick()
    fun onHistoryClick()
    fun onPasteClick()
    fun onMyWalletsClick()
    fun onAmountFocusChanged(isFocused: Boolean)
    fun onQuickAmountInput(input: Double)
    fun onWarningInfoClick()
}

@Composable
fun CrossChainSetupContent(
    state: CrossChainSetupViewState,
    callback: CrossChainSetupScreenInterface
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val showQuickInput = state.amountInputState.isFocused && state.isSoftKeyboardOpen
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            ToolbarBottomSheet(
                title = stringResource(id = R.string.common_title_cross_chain),
                onNavigationClick = callback::onNavigationClick
            )
            RouteDisclosure(
                capability = state.routeCapability,
                fallbackMessage = state.routeCapabilityMessage
            )

            MarginVertical(margin = 16.dp)
            SelectorWithBorder(
                state = state.originChainSelectorState,
                onClick = callback::onOriginChainClick
            )

            MarginVertical(margin = 12.dp)
            AmountInput(
                state = state.amountInputState,
                borderColorFocused = colorAccentDark,
                onTokenClick = callback::onAssetClick,
                onInput = callback::onAmountInput,
                onInputFocusChange = callback::onAmountFocusChanged
            )

            MarginVertical(margin = 12.dp)
            SelectorWithBorder(
                state = state.destinationChainSelectorState,
                onClick = callback::onDestinationChainClick
            )

            MarginVertical(margin = 12.dp)
            AddressInput(
                state = state.addressInputState,
                onInput = callback::onAddressInput,
                onInputClear = callback::onAddressInputClear,
                onPaste = callback::onPasteClick
            )
            MarginVertical(margin = 8.dp)
            AddressActions(
                walletIcon = state.walletIcon,
                callback = callback
            )

            state.warningInfoState?.let {
                MarginVertical(margin = 12.dp)
                WarningInfo(state = it, onClick = callback::onWarningInfoClick)
            }
            MarginVertical(margin = 12.dp)
            FeeInfo(state = state.originFeeInfoState, modifier = Modifier.defaultMinSize(minHeight = 52.dp))
            if (state.destinationFeeInfoState != null) {
                FeeInfo(state = state.destinationFeeInfoState, modifier = Modifier.defaultMinSize(minHeight = 52.dp))
            }
            MarginVertical(margin = 24.dp)
            RouteProviderInventory(state.routeInventory)
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
        ) {
            MarginVertical(margin = 12.dp)
            AccentButton(
                state = state.buttonState,
                onClick = {
                    keyboardController?.hide()
                    callback.onNextClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp)
            )
            MarginVertical(margin = 12.dp)
            if (showQuickInput) {
                QuickInput(
                    values = listOf(QuickAmountInput.P75, QuickAmountInput.P50, QuickAmountInput.P25).toTypedArray(),
                    onQuickAmountInput = {
                        keyboardController?.hide()
                        callback.onQuickAmountInput(it)
                    }
                )
            }
        }
    }
}

@Composable
internal fun RouteProviderInventory(items: List<CrossChainRouteInventoryItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cross_chain_route_inventory")
    ) {
        H3(text = stringResource(R.string.cross_chain_route_inventory_title))
        B0(
            text = stringResource(R.string.cross_chain_route_inventory_description),
            color = white50
        )
        if (items.isEmpty()) {
            B0(
                text = stringResource(R.string.cross_chain_route_inventory_checking),
                color = white50
            )
            return@Column
        }

        items.forEachIndexed { index, item ->
            MarginVertical(margin = 12.dp)
            Column(
                modifier = Modifier.testTag(
                    "cross_chain_route_inventory_${item.providerId}_$index"
                )
            ) {
                H3(text = item.protocolName)
                B0(text = routeInventoryStatus(item.status), color = white50)
                RouteInventoryCoverage(item)
                item.userFacingReason?.let { reason ->
                    B0(text = reason, color = white50)
                }
                B0(text = routeInventoryMinimum(item), color = white50)
                B0(
                    text = item.estimatedTime?.let { estimate ->
                        stringResource(R.string.cross_chain_route_estimated_time, estimate)
                    } ?: stringResource(R.string.cross_chain_route_estimated_time_unknown),
                    color = white50
                )
                item.warnings.forEach { warning ->
                    B0(
                        text = stringResource(R.string.cross_chain_route_warning, warning),
                        color = white50
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteInventoryCoverage(item: CrossChainRouteInventoryItem) {
    when {
        item.isExactRoute -> {
            val asset = item.assets.single()
            B0(
                text = stringResource(
                    R.string.cross_chain_route_inventory_exact_route,
                    item.originNetworks.single().name,
                    item.destinationNetworks.single().name,
                    asset.symbol
                ),
                color = white50
            )
            B0(
                text = stringResource(
                    R.string.cross_chain_route_inventory_asset_id,
                    asset.originAssetId
                ),
                color = white50
            )
        }
        item.hasCatalogCoverage -> {
            val networkScopedAssets = item.assets.joinToString { asset ->
                "${asset.originNetworkName} ${asset.symbol}"
            }
            B0(
                text = stringResource(
                    R.string.cross_chain_route_inventory_summary,
                    item.originNetworks.size,
                    item.destinationNetworks.size,
                    networkScopedAssets
                ),
                color = white50
            )
        }
        else -> B0(
            text = stringResource(R.string.cross_chain_route_inventory_no_catalog),
            color = white50
        )
    }
}

@Composable
private fun routeInventoryStatus(status: CrossChainRouteInventoryStatus): String =
    stringResource(
        when (status) {
            CrossChainRouteInventoryStatus.Available ->
                R.string.cross_chain_route_inventory_status_available
            CrossChainRouteInventoryStatus.ActionsPaused ->
                R.string.cross_chain_route_inventory_status_paused
            CrossChainRouteInventoryStatus.SetupRequired ->
                R.string.cross_chain_route_inventory_status_setup
            CrossChainRouteInventoryStatus.Unavailable ->
                R.string.cross_chain_route_inventory_status_unavailable
        }
    )

@Composable
private fun routeInventoryMinimum(item: CrossChainRouteInventoryItem): String = when {
    item.minimumAmount != null -> stringResource(
        R.string.cross_chain_route_minimum_value,
        item.minimumAmount,
        item.minimumAssetSymbol.orEmpty()
    )
    !item.hasCatalogCoverage -> stringResource(
        R.string.cross_chain_route_inventory_minimum_no_reviewed_value
    )
    !item.isExactRoute -> stringResource(
        R.string.cross_chain_route_inventory_minimum_select_route
    )
    else -> stringResource(R.string.cross_chain_route_inventory_minimum_not_supplied)
}

@Composable
private fun RouteDisclosure(
    capability: CrossChainRouteCapability?,
    fallbackMessage: String
) {
    if (capability == null) {
        B0(text = fallbackMessage, color = white50)
        return
    }

    B0(
        text = stringResource(R.string.cross_chain_route_protocol, capability.protocol.displayName),
        color = white50
    )
    capability.userFacingReason?.let { B0(text = it, color = white50) }
    if (capability.availability == CrossChainRouteAvailability.Available) {
        val minimum = capability.minimumAmount?.let { amount ->
            stringResource(
                R.string.cross_chain_route_minimum_value,
                amount,
                capability.minimumAssetSymbol.orEmpty()
            )
        } ?: stringResource(R.string.cross_chain_route_minimum_unknown)
        B0(text = minimum, color = white50)

        capability.originFee?.let { fee ->
            B0(
                text = stringResource(
                    R.string.cross_chain_route_origin_fee,
                    fee.amount,
                    fee.assetSymbol
                ),
                color = white50
            )
        }
        capability.destinationFee?.let { fee ->
            B0(
                text = stringResource(
                    R.string.cross_chain_route_destination_fee,
                    fee.amount,
                    fee.assetSymbol
                ),
                color = white50
            )
        }
        B0(
            text = capability.estimatedTime?.let { estimate ->
                stringResource(R.string.cross_chain_route_estimated_time, estimate)
            } ?: stringResource(R.string.cross_chain_route_estimated_time_unknown),
            color = white50
        )
        capability.warnings.forEach { warning ->
            B0(
                text = stringResource(R.string.cross_chain_route_warning, warning),
                color = white50
            )
        }
    }
}

@Composable
private fun AddressActions(
    walletIcon: Drawable?,
    callback: CrossChainSetupScreenInterface,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
    ) {
        Badge(
            iconResId = R.drawable.ic_scan,
            labelResId = R.string.chip_qr,
            onClick = callback::onQrClick
        )
        MarginHorizontal(12.dp)
        Badge(
            iconResId = R.drawable.ic_history_16,
            labelResId = R.string.chip_history,
            onClick = callback::onHistoryClick
        )
        Spacer(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 12.dp)
        )
        if (walletIcon != null) {
            Badge(
                icon = walletIcon,
                labelResId = R.string.chip_my_wallets,
                onClick = callback::onMyWalletsClick
            )
        }
    }
}

@Preview
@Composable
private fun CrossChainPreview() {
    val state = CrossChainSetupViewState(
        toolbarState = ToolbarViewState("Send Fund", R.drawable.ic_arrow_left_24),
        addressInputState = AddressInputState("Send to", "", ""),
        amountInputState = AmountInputViewState(
            "KSM",
            "",
            "1003 KSM",
            "$170000",
            BigDecimal("0.980"),
            "Amount",
            allowAssetChoose = true
        ),
        originChainSelectorState = SelectorState("Origin network", null, null),
        destinationChainSelectorState = SelectorState("Destination network", null, null),
        originFeeInfoState = FeeInfoViewState.default,
        destinationFeeInfoState = FeeInfoViewState.default,
        warningInfoState = null,
        buttonState = ButtonViewState("Continue", true),
        walletIcon = null,
        isSoftKeyboardOpen = false,
        routeCapabilityMessage = "Checking route…",
        routeCapability = null,
        routeInventory = emptyList()
    )

    val emptyCallback = object : CrossChainSetupScreenInterface {
        override fun onNavigationClick() {}
        override fun onAddressInput(input: String) {}
        override fun onAddressInputClear() {}
        override fun onAmountInput(input: BigDecimal?) {}
        override fun onOriginChainClick() {}
        override fun onDestinationChainClick() {}
        override fun onAssetClick() {}
        override fun onNextClick() {}
        override fun onQrClick() {}
        override fun onHistoryClick() {}
        override fun onPasteClick() {}
        override fun onMyWalletsClick() {}
        override fun onAmountFocusChanged(isFocused: Boolean) {}
        override fun onQuickAmountInput(input: Double) {}
        override fun onWarningInfoClick() {}
    }

    FearlessTheme {
        CrossChainSetupContent(
            state = state,
            callback = emptyCallback
        )
    }
}

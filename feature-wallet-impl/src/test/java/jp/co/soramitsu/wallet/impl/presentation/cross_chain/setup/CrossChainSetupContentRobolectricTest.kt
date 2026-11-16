package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import org.robolectric.RuntimeEnvironment
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WarningInfoState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.xcm.domain.CrossChainFeeQuote
import jp.co.soramitsu.xcm.domain.CrossChainProtocol
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h3000dp")
class CrossChainSetupContentRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : CrossChainSetupScreenInterface {
        override fun onNavigationClick() { calls += "back" }
        override fun onAddressInput(input: String) { calls += "address:$input" }
        override fun onAddressInputClear() { calls += "clear" }
        override fun onAmountInput(input: BigDecimal?) { calls += "amount:$input" }
        override fun onOriginChainClick() { calls += "origin" }
        override fun onDestinationChainClick() { calls += "destination" }
        override fun onAssetClick() { calls += "asset" }
        override fun onNextClick() { calls += "next" }
        override fun onQrClick() { calls += "qr" }
        override fun onHistoryClick() { calls += "history" }
        override fun onPasteClick() { calls += "paste" }
        override fun onMyWalletsClick() { calls += "wallets" }
        override fun onAmountFocusChanged(isFocused: Boolean) { calls += "focus:$isFocused" }
        override fun onQuickAmountInput(input: Double) { calls += "quick:$input" }
        override fun onWarningInfoClick() { calls += "warning" }
    }

    @Before
    fun setUp() {
        ComposeFixtures.passDoubleTapGuard()
    }

    @After
    fun tearDown() {
        unmockkObject(FwRestyle)
    }

    @Test
    fun availableRouteDisclosesProtocolFeesAndTimes() {
        render(
            state(
                capability = CrossChainRouteCapability(
                    providerId = "xcm",
                    protocol = CrossChainProtocol.ReviewedXcm,
                    availability = CrossChainRouteAvailability.Available,
                    actionsEnabled = true,
                    requiresAccount = true,
                    requiresSigning = true,
                    minimumAmount = "0.5",
                    minimumAssetSymbol = "DOT",
                    originFee = CrossChainFeeQuote("0.01", "DOT", live = true),
                    destinationFee = CrossChainFeeQuote("0.02", "DOT", live = false),
                    estimatedTime = "2 min",
                    warnings = listOf("Keep the existential deposit")
                )
            )
        )

        compose.onNodeWithText(string(R.string.cross_chain_route_protocol, "reviewed XCM")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_minimum_value, "0.5", "DOT")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_origin_fee, "0.01", "DOT")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_destination_fee, "0.02", "DOT")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_estimated_time, "2 min")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_warning, "Keep the existential deposit")).assertIsDisplayed()
    }

    @Test
    fun availableRouteWithoutValuesSaysTheyAreUnknown() {
        render(
            state(
                capability = CrossChainRouteCapability(
                    providerId = "xcm",
                    protocol = CrossChainProtocol.Liberland,
                    availability = CrossChainRouteAvailability.Available,
                    actionsEnabled = false,
                    requiresAccount = true,
                    requiresSigning = true,
                    userFacingReason = "Actions are paused"
                )
            )
        )

        compose.onNodeWithText(string(R.string.cross_chain_route_minimum_unknown)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_estimated_time_unknown)).assertIsDisplayed()
        compose.onNodeWithText("Actions are paused").assertIsDisplayed()
    }

    @Test
    fun userActionsReachTheCallback() {
        render(state(capability = null, warning = WarningInfoState("Low balance", listOf("Fee" to "1 DOT"))))

        compose.onNodeWithText("Checking route…").assertIsDisplayed()
        compose.onNodeWithText("Origin network").performClick()
        compose.onNodeWithText("Destination network").performClick()
        compose.onNodeWithText(string(R.string.chip_qr), ignoreCase = true).performClick()
        compose.onNodeWithText(string(R.string.chip_history), ignoreCase = true).performClick()
        compose.onNodeWithText(string(R.string.chip_my_wallets), ignoreCase = true).performClick()
        compose.onNodeWithText("Low balance").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.containsAll(listOf("origin", "destination", "qr", "history", "wallets", "warning", "next")))
    }

    @Test
    fun inventoryListsEveryProviderWithItsCoverage() {
        val polkadot = CrossChainRouteInventoryNetwork("polkadot", "Polkadot")
        val hub = CrossChainRouteInventoryNetwork("hub", "Asset Hub")
        val dot = CrossChainRouteInventoryAsset("polkadot", "Polkadot", "dot-id", "DOT")
        val usdt = CrossChainRouteInventoryAsset("hub", "Asset Hub", "usdt-id", "USDT")
        val items = listOf(
            inventory("exact", CrossChainRouteInventoryStatus.Available, listOf(polkadot), listOf(hub), listOf(dot), estimated = "1 min"),
            inventory("wide", CrossChainRouteInventoryStatus.ActionsPaused, listOf(polkadot, hub), listOf(hub), listOf(dot, usdt), reason = "Paused"),
            inventory("none", CrossChainRouteInventoryStatus.SetupRequired, emptyList(), emptyList(), emptyList()),
            inventory("down", CrossChainRouteInventoryStatus.Unavailable, listOf(polkadot), listOf(hub), listOf(dot), minimum = "2")
        )
        render(state(capability = null, inventory = items))

        compose.onNodeWithTag("cross_chain_route_inventory").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_inventory_status_available)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_inventory_status_paused)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_inventory_status_setup)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_inventory_status_unavailable)).assertIsDisplayed()
        assertEquals(2, compose.onAllNodes(hasText(string(R.string.cross_chain_route_inventory_exact_route, "Polkadot", "Asset Hub", "DOT"))).fetchSemanticsNodes().size)
        assertEquals(2, compose.onAllNodes(hasText(string(R.string.cross_chain_route_inventory_asset_id, "dot-id"))).fetchSemanticsNodes().size)
        compose.onNodeWithText(string(R.string.cross_chain_route_inventory_summary, 2, 1, "Polkadot DOT, Asset Hub USDT")).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_inventory_no_catalog)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_inventory_minimum_select_route)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_inventory_minimum_no_reviewed_value)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cross_chain_route_minimum_value, "2", "DOT")).assertIsDisplayed()
        compose.onNodeWithText("Paused").assertIsDisplayed()
    }

    @Test
    fun inventoryWithoutItemsSaysItIsChecking() {
        render(state(capability = null))

        compose.onNodeWithText(string(R.string.cross_chain_route_inventory_checking)).assertIsDisplayed()
    }

    @Test
    fun minimumTextExplainsAMissingReviewedValue() {
        val polkadot = CrossChainRouteInventoryNetwork("polkadot", "Polkadot")
        val hub = CrossChainRouteInventoryNetwork("hub", "Asset Hub")
        val dot = CrossChainRouteInventoryAsset("polkadot", "Polkadot", "dot-id", "DOT")
        val exact = inventory("exact", CrossChainRouteInventoryStatus.Available, listOf(polkadot), listOf(hub), listOf(dot))

        assertEquals(R.string.cross_chain_route_inventory_minimum_not_supplied to emptyList<String>(), routeInventoryMinimumText(exact))
        assertEquals(
            R.string.cross_chain_route_minimum_value to listOf("1", ""),
            routeInventoryMinimumText(exact.copy(minimumAmount = "1", minimumAssetSymbol = null))
        )
    }

    @Test
    fun focusedAmountWithKeyboardOffersQuickInputs() {
        render(state(capability = null, focused = true))

        compose.onNodeWithText("50%").performClick()
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.contains("quick:0.5"))
    }

    @Test
    fun legacyNoticeIsPlainText() {
        mockkObject(FwRestyle)
        every { FwRestyle.isEnabled(any()) } returns false

        render(state(capability = null))

        assertEquals(1, compose.onAllNodes(hasText("Checking route…")).fetchSemanticsNodes().size)
    }

    private fun inventory(
        id: String,
        status: CrossChainRouteInventoryStatus,
        origins: List<CrossChainRouteInventoryNetwork>,
        destinations: List<CrossChainRouteInventoryNetwork>,
        assets: List<CrossChainRouteInventoryAsset>,
        minimum: String? = null,
        estimated: String? = null,
        reason: String? = null
    ) = CrossChainRouteInventoryItem(
        providerId = id,
        protocolName = "Protocol $id",
        status = status,
        originNetworks = origins,
        destinationNetworks = destinations,
        assets = assets,
        minimumAmount = minimum,
        minimumAssetSymbol = minimum?.let { "DOT" },
        estimatedTime = estimated,
        warnings = listOf("Warning $id"),
        userFacingReason = reason
    )

    private fun render(state: CrossChainSetupViewState) {
        compose.setContent {
            FearlessAppTheme {
                CrossChainSetupContent(state = state, callback = callback)
            }
        }
        compose.waitForIdle()
    }

    private fun state(
        capability: CrossChainRouteCapability?,
        warning: WarningInfoState? = null,
        inventory: List<CrossChainRouteInventoryItem> = emptyList(),
        focused: Boolean = false
    ) = CrossChainSetupViewState(
        toolbarState = ToolbarViewState("Cross chain", R.drawable.ic_arrow_left_24),
        addressInputState = AddressInputState("Send to", "5Grwva", ""),
        amountInputState = AmountInputViewState(
            tokenName = "DOT",
            tokenImage = "",
            totalBalance = "Balance: 10 DOT",
            fiatAmount = "$60",
            tokenAmount = BigDecimal("1.5"),
            title = "Amount",
            isFocused = focused,
            allowAssetChoose = true
        ),
        originChainSelectorState = SelectorState("Origin network", "Polkadot", null),
        destinationChainSelectorState = SelectorState("Destination network", "Asset Hub", null),
        originFeeInfoState = FeeInfoViewState("Origin fee", "0.01 DOT", "$0.06"),
        destinationFeeInfoState = FeeInfoViewState("Destination fee", "0.02 DOT", "$0.12"),
        warningInfoState = warning,
        buttonState = ButtonViewState("Continue", true),
        walletIcon = BitmapDrawable(RuntimeEnvironment.getApplication().resources, Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)),
        isSoftKeyboardOpen = focused,
        routeCapabilityMessage = "Checking route…",
        routeCapability = capability,
        routeInventory = inventory
    )
}

package jp.co.soramitsu.app.root.presentation.main.hub

import android.app.Application
import android.content.Context
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.model.DeFiAccountRequirement
import jp.co.soramitsu.common.model.DeFiAvailability
import jp.co.soramitsu.common.model.DeFiCapability
import jp.co.soramitsu.common.model.DeFiSigningRequirement
import jp.co.soramitsu.xcm.domain.CrossChainProtocol
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MainHubScreenTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val actions = mutableListOf<String>()
    private val available = DeFiCapability("test", DeFiAvailability.Available, DeFiAccountRequirement.Sora,
        DeFiSigningRequirement.SignableAccount, setOf("sora"), true, true)

    @Before fun advanceClock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(10)) }

    private fun render(
        hub: MainHubType,
        swap: PolkaswapCapability = PolkaswapCapability(available, true),
        capabilities: Map<String, DeFiCapability> = emptyMap(),
        positions: DeFiPositionsSummary = DeFiPositionsSummary(),
        crossChain: CrossChainRouteCapability? = null
    ) {
        compose.setContent {
            FearlessAppTheme {
                MainHubScreen(hub, rememberScrollState(), { actions += "primary" }, { actions += "secondary" },
                    { actions += "farming" }, { actions += "market" }, swap, capabilities, positions, crossChain)
            }
        }
    }

    private fun click(label: String) {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(label).performScrollTo().assertIsEnabled().performClick()
    }

    @Test fun defiShowsReadyLoadingAndUnavailablePositionsAndRoutesFourDestinations() {
        val keys = listOf("staking", "liquidity-pools", "demeter-farming", "polkamarkt")
        render(MainHubType.Defi, capabilities = keys.associateWith { available.copy(featureId = it) },
            positions = DeFiPositionsSummary(DeFiPositionCount.Ready(3), DeFiPositionCount.Loading,
                DeFiPositionCount.Unavailable("Node offline"), DeFiPositionCount.Ready(2)))
        val staking = context.getString(R.string.main_hub_staking_action)
        compose.onNodeWithText(context.getString(R.string.main_hub_positions_count, staking, 3)).assertExists()
        compose.onNodeWithText(context.getString(R.string.main_hub_positions_loading, context.getString(R.string.main_hub_pools_action))).assertExists()
        compose.onNodeWithText("Node offline", substring = true).assertExists()
        click(staking)
        click(context.getString(R.string.main_hub_pools_action))
        click(context.getString(R.string.main_hub_farming_title))
        click(context.getString(R.string.main_hub_polkamarkt_title))
        assertEquals(listOf("primary", "secondary", "farming", "market"), actions)
    }

    @Test fun unavailableDefiDestinationsRemainVisibleWithTheirReason() {
        val blocked = available.copy(destinationEnabled = false, actionsEnabled = false, userFacingReason = "Add signing account")
        render(MainHubType.Defi, capabilities = mapOf("staking" to blocked))
        compose.onNodeWithText("Add signing account").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.main_hub_staking_action)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.main_hub_pools_action)).performScrollTo().assertIsNotEnabled()
        assertEquals(emptyList<String>(), actions)
    }

    @Test fun unacceptedDisclaimerRemainsReviewableWhenSwapsAreUnavailable() {
        render(MainHubType.Polkaswap, swap = PolkaswapCapability(DeFiCapability.checking("swap"), false))
        compose.onNodeWithText("Checking capability…").assertIsDisplayed()
        click("Review disclaimer")
        compose.onNodeWithText(context.getString(R.string.main_hub_pools_action)).assertIsNotEnabled()
        assertEquals(listOf("primary"), actions)
    }

    @Test fun availableSwapsOfferSwapAndPools() {
        render(MainHubType.Polkaswap)
        click("Swap tokens")
        click(context.getString(R.string.main_hub_pools_action))
        assertEquals(listOf("primary", "secondary"), actions)
    }

    @Test fun unavailableCrossChainOffersWithinNetworkFallback() {
        val capability = CrossChainRouteCapability("test", CrossChainProtocol.ReviewedXcm,
            CrossChainRouteAvailability.Unavailable, false, true, true, userFacingReason = "No reviewed route")
        render(MainHubType.CrossChain, crossChain = capability)
        compose.onNodeWithText("No reviewed route").assertIsDisplayed()
        click(context.getString(R.string.main_hub_cross_chain_action))
        click(context.getString(R.string.paused_cross_chain_send_within_network))
        assertEquals(listOf("primary", "secondary"), actions)
    }

    @Test fun availableCrossChainDisplaysProtocolWithoutFallback() {
        val capability = CrossChainRouteCapability("test", CrossChainProtocol.PolkaswapSoraEvm,
            CrossChainRouteAvailability.Available, true, true, true)
        render(MainHubType.CrossChain, crossChain = capability)
        compose.onNodeWithText("Protocol: Polkaswap SORA/EVM bridge").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.paused_cross_chain_send_within_network)).assertDoesNotExist()
        click(context.getString(R.string.main_hub_cross_chain_action))
        assertEquals(listOf("primary"), actions)
    }
}

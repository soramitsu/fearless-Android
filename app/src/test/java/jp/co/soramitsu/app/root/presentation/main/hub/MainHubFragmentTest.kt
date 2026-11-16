package jp.co.soramitsu.app.root.presentation.main.hub

import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.Navigation
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.app.HiltShim
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.HiltShimHostActivity
import jp.co.soramitsu.app.R
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.xcm.domain.CrossChainProtocol
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import jp.co.soramitsu.xcm.domain.CrossChainRouteProviderRegistry
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.junit.Assert.assertEquals
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** The hub screens as the tab graph shows them: the fragment, its view model and the stateless screen together. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class MainHubFragmentTest {
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(GlobalSnapshotApplyRule()).around(compose)
    private val walletRouter = mockk<WalletRouter>(relaxed = true)
    private val stakingChain = mockk<Chain>(relaxed = true) {
        every { id } returns "polkadot"
        every { ecosystem } returns Ecosystem.Substrate
        every { externalApi } returns null
        every { assets } returns listOf(mockk<ChainAsset> {
            every { staking } returns ChainAsset.StakingType.RELAYCHAIN
            every { supportStakingPool } returns false
        })
    }
    private val wallet = mockk<MetaAccount>(relaxed = true) {
        every { id } returns 7
        every { chainAccounts } returns emptyMap()
        every { substrateAccountId } returns ByteArray(32) { 1 }
    }
    private val accounts = mockk<AccountRepository>(relaxed = true) {
        coEvery { getSelectedMetaAccount() } returns wallet
        coEvery { isSigningRecoveryRequired(any(), any<IChain>()) } returns false
        coEvery { hasSigningMaterial(any(), any()) } returns true
    }
    private val registry = mockk<ChainRegistry>(relaxed = true) {
        coEvery { getChains() } returns listOf(stakingChain)
        coEvery { getRuntimeOrNull(any()) } returns mockk()
    }
    private val crossChain = mockk<CrossChainRouteProviderRegistry> {
        coEvery { bestCapability(any()) } returns CrossChainRouteCapability(
            "xcm", CrossChainProtocol.ReviewedXcm, CrossChainRouteAvailability.Unavailable, false, true, true,
            userFacingReason = "Cross-chain sends are paused"
        )
    }

    @Before
    fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        HiltShim.reset()
        HiltShim.inject(MainHubFragment::class) { }
        HiltShim.viewModel(MainHubViewModel::class) { handle ->
            MainHubViewModel(
                handle, walletRouter, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true) { every { observeHasReadDisclaimer() } returns flowOf() },
                mockk(relaxed = true), mockk(relaxed = true), accounts, registry, mockk(relaxed = true),
                mockk(relaxed = true), crossChain, mockk(relaxed = true)
            )
        }
    }

    @After
    fun tearDown() = HiltShim.reset()

    @Test
    fun theDefiHubOpensStakingInItsTab() {
        val tabs = mock(NavController::class.java)
        val fragment = show(MainHubType.Defi)
        Navigation.setViewNavController(fragment.requireView(), tabs)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(fragment.getString(R.string.main_hub_staking_action)).performScrollTo().performClick()
        idle()
        val navigations = mockingDetails(tabs).invocations.filter { it.method.name == "navigate" }
        assertEquals(listOf<Any>(R.id.stakingFragment), navigations.map { it.arguments[0] })
    }

    @Test
    fun theCrossChainHubExplainsThePauseAndOffersASameNetworkSend() {
        val fragment = show(MainHubType.CrossChain)
        compose.onNodeWithText("Cross-chain sends are paused").assertExists()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(fragment.getString(R.string.main_hub_cross_chain_action)).performClick()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText(fragment.getString(R.string.paused_cross_chain_send_within_network)).performScrollTo().performClick()
        verify(exactly = 1) { walletRouter.openCrossChainSend(null) }
        verify(exactly = 1) { walletRouter.openSend(null) }
    }

    private fun show(type: MainHubType): MainHubFragment {
        val host = HiltShim.host()
        val fragment = MainHubFragment().apply { arguments = bundleOf("hub_type" to type.wireValue) }
        host.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        idle()
        compose.waitForIdle()
        return fragment
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}

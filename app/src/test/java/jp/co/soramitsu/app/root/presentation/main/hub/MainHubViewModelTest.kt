package jp.co.soramitsu.app.root.presentation.main.hub

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.model.DeFiAvailability
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.liquiditypools.domain.interfaces.DemeterFarmingInteractor
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.domain.model.UserPoolData
import jp.co.soramitsu.polkamarkt.api.PolkamarktClaimable
import jp.co.soramitsu.polkamarkt.api.PolkamarktInteractor
import jp.co.soramitsu.polkamarkt.api.PolkamarktPosition
import jp.co.soramitsu.polkamarkt.api.PolkamarktRouter
import jp.co.soramitsu.polkamarkt.api.PolkamarktSnapshot
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.xcm.domain.CrossChainProtocol
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import jp.co.soramitsu.xcm.domain.CrossChainRouteProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.core.models.Asset as ChainAsset

@OptIn(ExperimentalCoroutinesApi::class)
class MainHubViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()

    private val sora = mockk<Chain>(relaxed = true) {
        every { id } returns SORA
        every { ecosystem } returns Ecosystem.Substrate
        every { externalApi } returns null
    }
    private val stakingChain = mockk<Chain>(relaxed = true) {
        every { id } returns "polkadot"
        every { ecosystem } returns Ecosystem.Substrate
        every { externalApi } returns null
        every { assets } returns listOf(mockk<ChainAsset> {
            every { staking } returns ChainAsset.StakingType.RELAYCHAIN
            every { supportStakingPool } returns false
        })
    }
    private val unsupportedChain = mockk<Chain>(relaxed = true) {
        every { id } returns "evm"
        every { ecosystem } returns Ecosystem.Ethereum
    }
    private val wallet = mockk<MetaAccount>(relaxed = true) {
        every { id } returns 7
        every { chainAccounts } returns emptyMap()
        every { substrateAccountId } returns ByteArray(32) { 1 }
    }
    private val accounts = mockk<AccountRepository> {
        coEvery { getSelectedMetaAccount() } returns wallet
        coEvery { isSigningRecoveryRequired(any(), any<IChain>()) } returns false
        coEvery { hasSigningMaterial(any(), any()) } returns true
    }
    private val registry = mockk<ChainRegistry> {
        coEvery { getChain(SORA) } returns sora
        coEvery { getChains() } returns listOf(stakingChain, unsupportedChain)
        coEvery { getRuntimeOrNull(any()) } returns mockk()
    }
    private val walletRepository = mockk<WalletRepository> {
        coEvery { getAssets(7) } returns listOf(asset(SORA, BigDecimal.TEN), asset(SORA, BigDecimal.ONE), stakedAsset())
    }
    private val swaps = mockk<PolkaswapInteractor> {
        every { polkaswapChainId } returns SORA
        every { hasReadDisclaimer } returns true
        every { observeHasReadDisclaimer() } returns flowOf()
    }
    private val toggles = mockk<ProductFeatureToggleStore> {
        every { polkaswapMutationsEnabled } returns true
        every { demeterMutationsEnabled } returns true
        every { polkamarktMutationsEnabled } returns true
    }
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "text-${firstArg<Int>()}" } }
    private val pools = mockk<PoolsInteractor> {
        every { subscribePoolsCacheCurrentAccount() } returns flowOf(listOf(pool(BigDecimal.ONE), pool(BigDecimal.ZERO), pool(null)))
        coEvery { updateAccountPools() } returns Unit
    }
    private val farming = mockk<DemeterFarmingInteractor> {
        coEvery { refresh(SORA) } returns Unit
        coEvery { getFarmedPools(SORA) } returns listOf(farm(BigDecimal.ONE, BigDecimal.ZERO), farm(BigDecimal.ZERO, BigDecimal.ZERO))
    }
    private val polkamarkt = mockk<PolkamarktInteractor> {
        coEvery { snapshot(any()) } returns mockk<PolkamarktSnapshot> {
            every { positions } returns listOf(position("m1"), position("m2"))
            every { claimable } returns listOf(claim("m2"), claim("m3"))
        }
    }
    private val crossChain = mockk<CrossChainRouteProviderRegistry>()
    private val walletRouter = mockk<WalletRouter>(relaxed = true)
    private val polkaswapRouter = mockk<PolkaswapRouter>(relaxed = true)
    private val polkamarktRouter = mockk<PolkamarktRouter>(relaxed = true)

    @Test
    fun defiHubReportsReadyDestinationsAndCountsOpenPositions() = hub(MainHubType.Defi) { vm ->
        val capabilities = vm.defiCapabilities.value
        listOf("staking", "liquidity-pools", "demeter-farming", "polkamarkt").forEach { feature ->
            val capability = capabilities.getValue(feature)
            assertEquals(feature, DeFiAvailability.Available, capability.availability)
            assertTrue(feature, capability.canOpenDestination)
            assertTrue(feature, capability.canPerformAction)
            assertNull(feature, capability.userFacingReason)
        }
        assertEquals(setOf("polkadot"), capabilities.getValue("staking").supportedNetworkIds)
        assertEquals(
            DeFiPositionsSummary(
                staking = DeFiPositionCount.Ready(1),
                liquidityPools = DeFiPositionCount.Ready(1),
                farming = DeFiPositionCount.Ready(1),
                polkamarkt = DeFiPositionCount.Ready(3)
            ),
            vm.defiPositions.value
        )

        assertTrue(vm.canOpenStaking())
        vm.onPrimaryAction()
        vm.onSecondaryAction()
        vm.onFarmingAction()
        vm.onPolkamarktAction()
        verify(exactly = 1) { polkaswapRouter.openPools() }
        verify(exactly = 1) { polkaswapRouter.openDemeterFarming() }
        verify(exactly = 1) { polkamarktRouter.openPolkamarkt(null) }
        verify(exactly = 0) { walletRouter.openCrossChainSend(any()) }
    }

    @Test
    fun pausedMutationsKeepDestinationsOpenButExplainWhyActionsAreOff() {
        every { toggles.polkaswapMutationsEnabled } returns false
        every { toggles.demeterMutationsEnabled } returns false
        every { toggles.polkamarktMutationsEnabled } returns false
        hub(MainHubType.Defi) { vm ->
            val capabilities = vm.defiCapabilities.value
            assertEquals("text-${R.string.paused_liquidity_message}", capabilities.getValue("liquidity-pools").userFacingReason)
            assertEquals("text-${R.string.paused_farming_message}", capabilities.getValue("demeter-farming").userFacingReason)
            assertEquals("text-${R.string.paused_polkamarkt_message}", capabilities.getValue("polkamarkt").userFacingReason)
            listOf("liquidity-pools", "demeter-farming", "polkamarkt").forEach { feature ->
                assertTrue(feature, capabilities.getValue(feature).canOpenDestination)
                assertFalse(feature, capabilities.getValue(feature).canPerformAction)
            }
        }
    }

    @Test
    fun soraPrerequisitesExplainWhichSetupIsMissing() {
        val cases = listOf<Pair<() -> Any, String>>(
            { every { wallet.substrateAccountId } returns null } to "Add a SORA account to continue.",
            { coEvery { accounts.isSigningRecoveryRequired(7, sora) } returns true } to "Recover this wallet's signing material to continue.",
            { coEvery { accounts.hasSigningMaterial(7, sora) } throws IllegalStateException("locked") } to
                "This SORA account is watch-only or needs an unsupported external signer.",
            { coEvery { registry.getRuntimeOrNull(SORA) } returns null } to "SORA runtime is unavailable."
        )
        cases.forEach { (arrange, reason) ->
            every { wallet.substrateAccountId } returns ByteArray(32) { 1 }
            coEvery { accounts.isSigningRecoveryRequired(any(), any<IChain>()) } returns false
            coEvery { accounts.hasSigningMaterial(any(), any()) } returns true
            coEvery { registry.getRuntimeOrNull(any()) } returns mockk()
            arrange()
            hub(MainHubType.Defi) { vm ->
                val pools = vm.defiCapabilities.value.getValue("liquidity-pools")
                assertEquals(reason, pools.userFacingReason)
                assertFalse(reason, pools.canOpenDestination)
                assertTrue("Polkamarkt stays browsable: $reason", vm.defiCapabilities.value.getValue("polkamarkt").canOpenDestination)
                vm.onSecondaryAction()
                vm.onFarmingAction()
                verify(exactly = 0) { polkaswapRouter.openPools() }
                verify(exactly = 0) { polkaswapRouter.openDemeterFarming() }
            }
        }
    }

    @Test
    fun lookupFailuresBecomeUnavailableCapabilitiesAndPositions() {
        coEvery { accounts.getSelectedMetaAccount() } throws IllegalStateException("database closed")
        every { pools.subscribePoolsCacheCurrentAccount() } returns flow { throw IllegalStateException("pool cache") }
        coEvery { pools.updateAccountPools() } throws IllegalStateException("pool refresh")
        coEvery { farming.refresh(SORA) } throws IllegalStateException("farm refresh")
        coEvery { polkamarkt.snapshot(any()) } throws IllegalStateException("indexer")
        hub(MainHubType.Defi) { vm ->
            val capabilities = vm.defiCapabilities.value
            assertEquals("database closed", capabilities.getValue("staking").userFacingReason)
            assertEquals(DeFiAvailability.Unavailable, capabilities.getValue("liquidity-pools").availability)
            assertEquals("database closed", capabilities.getValue("demeter-farming").userFacingReason)
            assertFalse(vm.canOpenStaking())
            assertEquals(
                DeFiPositionsSummary(
                    staking = DeFiPositionCount.Unavailable("database closed"),
                    liquidityPools = DeFiPositionCount.Unavailable("pool cache"),
                    farming = DeFiPositionCount.Unavailable("farm refresh"),
                    polkamarkt = DeFiPositionCount.Unavailable("indexer")
                ),
                vm.defiPositions.value
            )
            vm.onPolkamarktAction()
            verify(exactly = 0) { polkamarktRouter.openPolkamarkt(any()) }
        }
    }

    @Test
    fun aFailedPoolRefreshOnlyReplacesAStillLoadingCount() {
        every { pools.subscribePoolsCacheCurrentAccount() } returns MutableSharedFlow()
        coEvery { pools.updateAccountPools() } throws IllegalStateException()
        hub(MainHubType.Defi) { vm ->
            assertEquals(DeFiPositionCount.Unavailable("Open Liquidity pools to refresh."), vm.defiPositions.value.liquidityPools)
        }
    }

    @Test
    fun stakingCapabilityNamesTheFirstMissingRequirement() {
        val network = setOf("polkadot")
        fun build(account: Boolean = true, recovery: Boolean = false, signing: Boolean = true, runtime: Boolean = true) =
            buildStakingCapability(network, account, recovery, signing, runtime)

        assertEquals(DeFiAvailability.Available, build().availability)
        assertEquals("No supported staking network is available.", buildStakingCapability(emptySet(), true, false, true, true).userFacingReason)
        assertEquals(DeFiAvailability.Unavailable, buildStakingCapability(emptySet(), true, false, true, true).availability)
        assertEquals("Add a Substrate account on a supported staking network.", build(account = false).userFacingReason)
        assertEquals("Recover this wallet's signing material to stake.", build(recovery = true).userFacingReason)
        assertEquals("This wallet is watch-only or needs an unsupported external signer.", build(signing = false).userFacingReason)
        assertEquals(DeFiAvailability.SetupRequired, build(signing = false).availability)
        assertEquals("The staking runtime is unavailable.", build(runtime = false).userFacingReason)
        assertEquals(DeFiAvailability.Unavailable, build(runtime = false).availability)
        assertEquals("boom", buildStakingCapability(network, true, false, true, true, lookupFailure = "boom").userFacingReason)
    }

    @Test
    fun stakingNeedsAnAccountWithSigningMaterialOnASupportedNetwork() {
        coEvery { accounts.isSigningRecoveryRequired(7, stakingChain) } returns true
        hub(MainHubType.Defi) { vm ->
            assertEquals(DeFiAvailability.SetupRequired, vm.defiCapabilities.value.getValue("staking").availability)
            assertFalse(vm.canOpenStaking())
        }
        coEvery { accounts.isSigningRecoveryRequired(any(), any<IChain>()) } returns false
        coEvery { registry.getRuntimeOrNull("polkadot") } returns null
        hub(MainHubType.Defi) { vm ->
            assertEquals("The staking runtime is unavailable.", vm.defiCapabilities.value.getValue("staking").userFacingReason)
        }
    }

    @Test
    fun polkaswapHubSendsUnacceptedDisclaimersToReviewFirst() {
        every { swaps.hasReadDisclaimer } returns false
        hub(MainHubType.Polkaswap) { vm ->
            val capability = vm.polkaswapCapability.value
            assertFalse(capability.disclaimerAccepted)
            assertEquals("Review disclaimer", capability.actionLabel)
            assertTrue(capability.canOpenAction)
            vm.onPrimaryAction()
            vm.onSecondaryAction()
            verify(exactly = 1) { polkaswapRouter.openPolkaswapDisclaimerFromMainScreen() }
            verify(exactly = 0) { walletRouter.openSwapTokensScreen(any(), any(), any()) }
            verify(exactly = 1) { polkaswapRouter.openPools() }
        }
    }

    @Test
    fun polkaswapHubAsksForASecondAssetAndReportsLookupFailures() {
        coEvery { walletRepository.getAssets(7) } returns listOf(asset(SORA, BigDecimal.ONE))
        hub(MainHubType.Polkaswap) { vm ->
            assertEquals("Add another supported SORA asset before swapping.", vm.polkaswapCapability.value.reason)
            assertFalse(vm.polkaswapCapability.value.canOpenAction)
        }
        every { toggles.polkaswapMutationsEnabled } returns false
        coEvery { walletRepository.getAssets(7) } returns listOf(asset(SORA, BigDecimal.ONE), asset(SORA, BigDecimal.ONE))
        hub(MainHubType.Polkaswap) { vm ->
            assertEquals("text-${R.string.paused_swap_message}", vm.polkaswapCapability.value.reason)
            assertTrue(vm.polkaswapCapability.value.defiCapability.canOpenDestination)
        }
        coEvery { registry.getChain(SORA) } throws IllegalStateException("no sora")
        hub(MainHubType.Polkaswap) { vm ->
            assertEquals("no sora", vm.polkaswapCapability.value.reason)
            assertEquals(DeFiAvailability.Unavailable, vm.polkaswapCapability.value.defiCapability.availability)
            vm.onSecondaryAction()
            verify(exactly = 0) { polkaswapRouter.openPools() }
        }
    }

    @Test
    fun acceptingTheDisclaimerRefreshesTheSwapCapability() {
        val accepted = MutableSharedFlow<Boolean>()
        every { swaps.hasReadDisclaimer } returns false
        every { swaps.observeHasReadDisclaimer() } returns accepted
        hub(MainHubType.Polkaswap) { vm ->
            assertFalse(vm.polkaswapCapability.value.disclaimerAccepted)
            every { swaps.hasReadDisclaimer } returns true
            accepted.emit(true)
            runCurrent()
            assertTrue(vm.polkaswapCapability.value.disclaimerAccepted)
            assertEquals("Swap tokens", vm.polkaswapCapability.value.actionLabel)
        }
    }

    @Test
    fun crossChainHubShowsTheBestRouteAndKeepsASameNetworkFallback() {
        val capability = CrossChainRouteCapability(
            "xcm", CrossChainProtocol.ReviewedXcm, CrossChainRouteAvailability.Unavailable, false, true, true,
            userFacingReason = "Paused"
        )
        coEvery { crossChain.bestCapability(any()) } returns capability
        hub(MainHubType.CrossChain) { vm ->
            assertEquals(capability, vm.crossChainCapability.value)
            vm.onPrimaryAction()
            vm.onSecondaryAction()
            verify(exactly = 1) { walletRouter.openCrossChainSend(null) }
            verify(exactly = 1) { walletRouter.openSend(null) }
            verify(exactly = 0) { polkaswapRouter.openPools() }
        }
    }

    private fun hub(type: MainHubType, assertions: suspend TestScope.(MainHubViewModel) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val vm = MainHubViewModel(
                SavedStateHandle(mapOf("hub_type" to type.wireValue)), walletRouter, polkaswapRouter, polkamarktRouter,
                polkamarkt, swaps, pools, farming, accounts, registry, walletRepository, toggles, crossChain, resources
            )
            try {
                runCurrent()
                assertEquals(type, vm.hubType)
                assertions(vm)
            } finally {
                vm.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun asset(chain: String, transferable: BigDecimal?) = mockk<Asset>(relaxed = true) {
        every { chainId } returns chain
        every { this@mockk.transferable } returns transferable
        every { bondedInPlanks } returns null
        every { unbondingInPlanks } returns null
        every { redeemableInPlanks } returns null
    }

    private fun stakedAsset() = mockk<Asset>(relaxed = true) {
        every { chainId } returns "polkadot"
        every { transferable } returns BigDecimal.ONE
        every { bondedInPlanks } returns BigInteger.ZERO
        every { unbondingInPlanks } returns BigInteger.ZERO
        every { redeemableInPlanks } returns BigInteger.TEN
    }

    private fun pool(base: BigDecimal?) = mockk<CommonPoolData> {
        every { user } returns base?.let { UserPoolData(it, BigDecimal.ZERO, 0.0, BigDecimal.ZERO) }
    }

    private fun farm(amount: BigDecimal, reward: BigDecimal) = mockk<DemeterFarmingPool> {
        every { this@mockk.amount } returns amount
        every { amountReward } returns reward
    }

    private fun position(market: String) = mockk<PolkamarktPosition> { every { marketId } returns market }
    private fun claim(market: String) = mockk<PolkamarktClaimable> { every { marketId } returns market }

    private companion object {
        const val SORA = "sora-main"
    }
}

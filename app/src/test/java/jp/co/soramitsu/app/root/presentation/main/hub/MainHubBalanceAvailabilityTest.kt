package jp.co.soramitsu.app.root.presentation.main.hub

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.*
import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.model.DeFiAvailability
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainHubBalanceAvailabilityTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private class Fixture(amounts: List<BigDecimal?>) {
        val chain = mockk<Chain>(relaxed = true) {
            every { id } returns "sora-fixture"
            every { ecosystem } returns Ecosystem.Substrate
            every { externalApi } returns null
        }
        val wallet = mockk<MetaAccount>(relaxed = true) {
            every { id } returns 7
            every { chainAccounts } returns emptyMap()
            every { substrateAccountId } returns ByteArray(32) { 1 }
        }
        val accounts = mockk<AccountRepository> {
            coEvery { getSelectedMetaAccount() } returns wallet
            coEvery { isSigningRecoveryRequired(7, chain) } returns false
            coEvery { getSubstrateSecrets(7) } returns mockk()
        }
        val registry = mockk<ChainRegistry> {
            coEvery { getChain("sora-fixture") } returns chain
            coEvery { getRuntimeOrNull("sora-fixture") } returns mockk()
        }
        val assets = amounts.map { amount -> mockk<Asset> {
            every { chainId } returns "sora-fixture"
            every { transferable } returns amount
        } }
        val repository = mockk<WalletRepository> { coEvery { getAssets(7) } returns assets }
        val swaps = mockk<PolkaswapInteractor> {
            every { polkaswapChainId } returns "sora-fixture"
            every { hasReadDisclaimer } returns true
            every { observeHasReadDisclaimer() } returns emptyFlow()
        }
        val toggles = mockk<ProductFeatureToggleStore> { every { polkaswapMutationsEnabled } returns true }
        val resources = mockk<ResourceManager> { every { getString(any()) } answers { "text-${firstArg<Int>()}" } }
        val router = mockk<WalletRouter>(relaxed = true)
        val vm = MainHubViewModel(SavedStateHandle(), router, mockk(), mockk(), mockk(), swaps,
            mockk(), mockk(), accounts, registry, repository, toggles, mockk(), resources)
    }

    @Test fun unavailableHoldingCannotAuthorizeSwapOrBeReportedAsKnownUnfunded() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            for (amounts in listOf(emptyList(), listOf(null, BigDecimal.ZERO), listOf(null, null))) {
                val f = Fixture(amounts)
                try {
                    runCurrent()
                    assertFalse(f.vm.polkaswapCapability.value.canOpenAction)
                    assertEquals(DeFiAvailability.Unavailable, f.vm.polkaswapCapability.value.defiCapability.availability)
                    f.vm.onPrimaryAction()
                    verify(exactly = 0) { f.router.openSwapTokensScreen(any(), any(), any()) }
                    assertEquals("text-${R.string.portfolio_balance_unavailable}", f.vm.polkaswapCapability.value.reason)
                } finally { f.vm.viewModelScope.coroutineContext.job.cancelAndJoin() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun knownZeroAndKnownFundingRetainDistinctRoutingBehavior() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            for (amount in listOf(BigDecimal.ZERO, BigDecimal.TEN)) {
                val f = Fixture(listOf(amount, BigDecimal.ZERO))
                try {
                    runCurrent()
                    assertEquals(amount.signum() > 0, f.vm.polkaswapCapability.value.canOpenAction)
                    if (amount.signum() == 0) {
                        assertEquals("Fund a SORA asset before swapping.", f.vm.polkaswapCapability.value.reason)
                        assertEquals(DeFiAvailability.SetupRequired, f.vm.polkaswapCapability.value.defiCapability.availability)
                    }
                    f.vm.onPrimaryAction()
                    verify(exactly = if (amount.signum() > 0) 1 else 0) { f.router.openSwapTokensScreen("sora-fixture", null, null) }
                } finally { f.vm.viewModelScope.coroutineContext.job.cancelAndJoin() }
            }
        } finally { Dispatchers.resetMain() }
    }
}

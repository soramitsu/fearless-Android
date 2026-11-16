package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.liquiditypools.domain.LiquidityMutationAction
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.VAL_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.XOR_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.walletAsset
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class LiquidityAddConfirmPresenterTest {

    private val ids = StringPair(XOR_ID, VAL_ID)
    private val routes = MutableSharedFlow<LiquidityPoolsNavGraphRoute>(replay = 1)
    private val router = mockk<InternalPoolsRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns routes
    }
    private val chain = mockk<Chain>(relaxed = true) {
        every { assets } returns listOf(xor, valToken)
        every { externalApi } returns null
    }
    private val pools = mockk<PoolsInteractor>(relaxed = true) {
        every { poolsChainId } returns soraMainChainId
        coEvery { isPairEnabled(XOR_ID, VAL_ID) } returns true
        coEvery { calcAddLiquidityNetworkFee(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns FEE
        coEvery { mutationCapabilityReason(LiquidityMutationAction.Add) } returns null
    }
    private val wallet = mockk<WalletInteractor> {
        every { assetsFlow() } returns flowOf(
            listOf(walletAsset(valToken), walletAsset(xor)).map { AssetWithStatus(it, hasAccount = true, hasChainAccount = true) }
        )
        every { assetFlow(soraMainChainId, "xor") } returns flowOf(walletAsset(xor))
        coEvery { getChain(soraMainChainId) } returns chain
    }
    private val chains = mockk<ChainsRepository> { coEvery { getChain(soraMainChainId) } returns chain }
    private val accounts = mockk<AccountInteractor> { coEvery { selectedMetaAccount() } returns mockk(relaxed = true) }
    private val resources = mockk<ResourceManager> {
        every { getString(R.string.lp_liquidity_add_complete_text) } returns "Liquidity added"
    }

    private fun TestScope.presenter(): Pair<LiquidityAddConfirmPresenter, StateFlow<LiquidityAddConfirmState>> {
        val store = mockk<CoroutinesStore> {
            every { uiScope } returns backgroundScope
            every { ioScope } returns backgroundScope
        }
        val presenter = LiquidityAddConfirmPresenter(store, router, wallet, chains, pools, accounts, resources)
        val state = presenter.createScreenStateFlow(backgroundScope)
        routes.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen(ids, BigDecimal.ONE, BigDecimal("2"), "12%"))
        settle()
        return presenter to state
    }

    @Test
    fun `confirmation shows the reviewed amounts, yield, slippage and fee`() = runTest {
        val (presenter, state) = presenter()

        val value = state.value
        assertEquals(xor, value.assetBase)
        assertEquals(valToken, value.assetTarget)
        assertEquals(BigDecimal.ONE.formatCrypto("XOR"), value.baseAmount)
        assertEquals(BigDecimal("2").formatCrypto("VAL"), value.targetAmount)
        assertEquals("12%", value.apy)
        assertEquals("0.5%", value.slippage)
        assertEquals("0.5%", "${presenter.stateSlippage.value}%")
        assertEquals(FEE.formatCryptoDetail("XOR"), value.feeInfo.feeAmount)
        assertTrue(value.buttonEnabled)
    }

    @Test
    fun `confirming submits the deposit, returns to the pool and shows success`() = runTest {
        coEvery {
            pools.observeAddLiquidity(soraMainChainId, xor, valToken, BigDecimal.ONE, BigDecimal("2"), true, true, 0.5)
        } returns "0xadd"
        val (presenter, state) = presenter()

        presenter.onConfirmClick()
        assertTrue(state.value.buttonLoading)
        settle()

        verifyOrder {
            router.back()
            router.back()
            router.back()
            router.openSuccessScreen("0xadd", soraMainChainId, "Liquidity added")
            router.supplyLiquidityCompleted()
        }
        coVerify { pools.updateAccountPools() }
        assertFalse(state.value.buttonLoading)
    }

    @Test
    fun `a rejected deposit shows its error and stays on the screen`() = runTest {
        coEvery { pools.observeAddLiquidity(any(), any(), any(), any(), any(), any(), any(), any()) } throws
            IllegalStateException("Balance changed")
        val (presenter, _) = presenter()

        presenter.onConfirmClick()
        settle()

        verify { router.openErrorsScreen(message = "Balance changed") }
        verify(exactly = 0) { router.openSuccessScreen(any(), any(), any()) }
    }

    @Test
    fun `switched off liquidity disables confirmation without submitting`() = runTest {
        coEvery { pools.mutationCapabilityReason(LiquidityMutationAction.Add) } returns "Liquidity paused"
        val (presenter, state) = presenter()
        assertFalse(state.value.buttonEnabled)

        presenter.onConfirmClick()
        settle()
        presenter.onAddItemClick(1)

        assertEquals("Liquidity paused", state.value.capabilityReason)
        coVerify(exactly = 0) { pools.observeAddLiquidity(any(), any(), any(), any(), any(), any(), any(), any()) }
        verify { router.openInfoScreen(1) }
    }

    private fun TestScope.settle() {
        advanceTimeBy(2_000)
        runCurrent()
    }

    private companion object {
        val FEE = BigDecimal("0.01")
    }
}

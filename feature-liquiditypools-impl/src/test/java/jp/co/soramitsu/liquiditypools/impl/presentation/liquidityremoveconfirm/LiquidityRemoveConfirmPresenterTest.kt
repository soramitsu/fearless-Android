package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremoveconfirm

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
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
class LiquidityRemoveConfirmPresenterTest {

    private val ids = StringPair(XOR_ID, VAL_ID)
    private val routes = MutableSharedFlow<LiquidityPoolsNavGraphRoute>(replay = 1)
    private val router = mockk<InternalPoolsRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns routes
    }
    private val chain = mockk<Chain>(relaxed = true) { every { assets } returns listOf(xor, valToken) }
    private val pools = mockk<PoolsInteractor>(relaxed = true) {
        every { poolsChainId } returns soraMainChainId
        coEvery { calcRemoveLiquidityNetworkFee(xor, valToken) } returns FEE
        coEvery { mutationCapabilityReason(LiquidityMutationAction.Remove) } returns null
    }
    private val wallet = mockk<WalletInteractor> {
        every { assetsFlow() } returns flowOf(
            listOf(walletAsset(xor), walletAsset(valToken)).map { AssetWithStatus(it, hasAccount = true, hasChainAccount = true) }
        )
        every { assetFlow(soraMainChainId, "xor") } returns flowOf(walletAsset(xor))
    }
    private val chains = mockk<ChainsRepository> { coEvery { getChain(soraMainChainId) } returns chain }
    private val resources = mockk<ResourceManager> {
        every { getString(R.string.lp_liquidity_add_complete_text) } returns "Done"
    }

    private fun TestScope.presenter(): Pair<LiquidityRemoveConfirmPresenter, StateFlow<LiquidityRemoveConfirmState>> {
        val store = mockk<CoroutinesStore> {
            every { uiScope } returns backgroundScope
            every { ioScope } returns backgroundScope
        }
        val presenter = LiquidityRemoveConfirmPresenter(store, router, wallet, chains, pools, resources)
        val state = presenter.createScreenStateFlow(backgroundScope)
        routes.tryEmit(
            LiquidityPoolsNavGraphRoute.LiquidityRemoveConfirmScreen(
                ids, BigDecimal.ONE, BigDecimal("2"), BigDecimal("0.9"), BigDecimal("1.9"), BigDecimal("3")
            )
        )
        settle()
        return presenter to state
    }

    @Test
    fun `confirmation shows both withdrawn amounts with their fiat value and the fee`() = runTest {
        val (_, state) = presenter()

        val value = state.value
        assertEquals(xor.iconUrl, value.assetBaseIconUrl)
        assertEquals(valToken.iconUrl, value.assetTargetIconUrl)
        assertEquals(BigDecimal.ONE.formatCrypto("XOR"), value.baseAmount)
        assertEquals(BigDecimal("2").formatFiat("$"), value.baseFiat)
        assertEquals(BigDecimal("2").formatCrypto("VAL"), value.targetAmount)
        assertEquals(BigDecimal("4").formatFiat("$"), value.targetFiat)
        assertEquals(FEE.formatCryptoDetail("XOR"), value.feeInfo.feeAmount)
        assertTrue(value.buttonEnabled)
    }

    @Test
    fun `confirming withdraws the reviewed pool tokens and shows success`() = runTest {
        coEvery {
            pools.observeRemoveLiquidity(soraMainChainId, xor, valToken, BigDecimal("3"), BigDecimal("0.9"), BigDecimal("1.9"), FEE)
        } returns "0xremove"
        val (presenter, state) = presenter()

        presenter.onRemoveConfirmClick()
        settle()

        verifyOrder {
            router.back()
            router.back()
            router.back()
            router.openSuccessScreen("0xremove", soraMainChainId, "Done")
        }
        coVerify { pools.updateAccountPools() }
        assertFalse(state.value.buttonLoading)
    }

    @Test
    fun `a rejected withdrawal shows its error`() = runTest {
        coEvery { pools.observeRemoveLiquidity(any(), any(), any(), any(), any(), any(), any()) } throws
            IllegalStateException("Position changed")
        val (presenter, _) = presenter()

        presenter.onRemoveConfirmClick()
        settle()

        verify { router.openErrorsScreen(message = "Position changed") }
        verify(exactly = 0) { router.openSuccessScreen(any(), any(), any()) }
    }

    @Test
    fun `switched off removal disables confirmation without submitting`() = runTest {
        coEvery { pools.mutationCapabilityReason(LiquidityMutationAction.Remove) } returns "Removal paused"
        val (presenter, state) = presenter()

        presenter.onRemoveConfirmClick()
        settle()
        presenter.onRemoveConfirmItemClick(2)

        assertEquals("Removal paused", state.value.capabilityReason)
        assertFalse(state.value.buttonEnabled)
        coVerify(exactly = 0) { pools.observeRemoveLiquidity(any(), any(), any(), any(), any(), any(), any()) }
        verify { router.openInfoScreen(2) }
    }

    private fun TestScope.settle() {
        advanceTimeBy(2_000)
        runCurrent()
    }

    private companion object {
        val FEE = BigDecimal("0.01")
    }
}

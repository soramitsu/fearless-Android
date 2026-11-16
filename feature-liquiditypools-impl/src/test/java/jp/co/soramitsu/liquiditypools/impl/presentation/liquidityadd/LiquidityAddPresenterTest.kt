package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.formatPercent
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.liquiditypools.domain.LiquidityMutationAction
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.VAL_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.XOR_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.walletAsset
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.usecase.ValidateAddLiquidityUseCase
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class LiquidityAddPresenterTest {

    private val ids = StringPair(XOR_ID, VAL_ID)
    private val routes = MutableSharedFlow<LiquidityPoolsNavGraphRoute>(replay = 1)
    private val router = mockk<InternalPoolsRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns routes
    }
    private val chain = mockk<Chain>(relaxed = true) {
        every { id } returns soraMainChainId
        every { assets } returns listOf(xor, valToken)
        every { externalApi } returns null
    }
    private var reserves = BasicPoolData(xor, valToken, BigDecimal("1000"), BigDecimal("2000"), BigDecimal("300"), "reserve")
    private val pools = mockk<PoolsInteractor> {
        every { poolsChainId } returns soraMainChainId
        coEvery { isPairEnabled(XOR_ID, VAL_ID) } returns true
        coEvery { getPoolData(XOR_ID, VAL_ID) } answers { flowOf(CommonPoolData(reserves, null)) }
        coEvery { mutationCapabilityReason(LiquidityMutationAction.Add) } returns null
        coEvery { getSbApy("reserve") } returns 12.5
        coEvery { calcAddLiquidityNetworkFee(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns FEE
    }
    private var targetBalance = walletAsset(valToken, free = BigDecimal("50"))
    private val wallet = mockk<WalletInteractor> {
        coEvery { getCurrentAssetOrNull(soraMainChainId, "xor") } returns walletAsset(xor)
        coEvery { getCurrentAssetOrNull(soraMainChainId, "val") } answers { targetBalance }
        coEvery { getCurrentAsset(soraMainChainId, "xor") } returns walletAsset(xor)
        coEvery { getChain(soraMainChainId) } returns chain
        every { assetFlow(soraMainChainId, "xor") } returns flowOf(walletAsset(xor))
    }
    private val chains = mockk<ChainsRepository> { coEvery { getChain(soraMainChainId) } returns chain }
    private val accounts = mockk<AccountInteractor> { coEvery { selectedMetaAccount() } returns mockk(relaxed = true) }
    private val resources = mockk<ResourceManager>(relaxed = true) {
        every { getString(R.string.common_available_format, *anyVararg()) } answers {
            "Available " + (args[1] as Array<*>).joinToString()
        }
    }

    private fun TestScope.presenter(): Pair<LiquidityAddPresenter, StateFlow<LiquidityAddState>> {
        val store = mockk<CoroutinesStore> {
            every { uiScope } returns backgroundScope
            every { ioScope } returns backgroundScope
        }
        val presenter = LiquidityAddPresenter(store, router, wallet, chains, pools, accounts, resources, ValidateAddLiquidityUseCase())
        val state = presenter.createScreenStateFlow(backgroundScope)
        backgroundScope.launch { state.collect {} }
        routes.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityAddScreen(ids))
        settle()
        return presenter to state
    }

    @Test
    fun `opened pool shows both balances, the yield, slippage and the network fee`() = runTest {
        val (_, state) = presenter()

        val value = state.value
        assertEquals("XOR", value.baseAmountInputViewState.tokenName)
        assertEquals("VAL", value.targetAmountInputViewState.tokenName)
        assertTrue(value.baseAmountInputViewState.totalBalance.startsWith("Available "))
        assertEquals(BigDecimal("12.5").formatPercent() + "%", value.apy)
        assertEquals("0.5%", value.slippage)
        assertEquals(FEE.formatCryptoDetail("XOR"), value.feeInfo.feeAmount)
        assertEquals(BigDecimal("0.02").formatFiat("$"), value.feeInfo.feeAmountFiat)
        assertFalse("nothing entered yet", value.buttonEnabled)
    }

    @Test
    fun `entering the base amount fills the target at the pool ratio and enables review`() = runTest {
        val (presenter, state) = presenter()

        presenter.onAddBaseAmountChange(BigDecimal("1.5"))
        runCurrent()
        assertTrue(state.value.targetAmountInputViewState.isShimmerAmounts)
        settle()

        assertEquals(0, BigDecimal("3").compareTo(state.value.targetAmountInputViewState.tokenAmount))
        assertEquals(BigDecimal("3").multiply(BigDecimal("2")).formatFiat("$"), state.value.targetAmountInputViewState.fiatAmount)
        assertTrue(state.value.buttonEnabled)
    }

    @Test
    fun `entering the target amount fills the base amount`() = runTest {
        val (presenter, state) = presenter()

        presenter.onAddTargetAmountFocusChange(true)
        presenter.onAddTargetAmountChange(BigDecimal("4"))
        settle()

        assertEquals(0, BigDecimal("2").compareTo(state.value.baseAmountInputViewState.tokenAmount))
        assertTrue(state.value.targetAmountInputViewState.isFocused)
        presenter.onAddBaseAmountFocusChange(true)
        runCurrent()
        assertTrue(state.value.baseAmountInputViewState.isFocused)
    }

    @Test
    fun `an empty pool keeps the entered amount as the other side`() = runTest {
        reserves = reserves.copy(targetReserves = BigDecimal.ZERO)
        val (presenter, state) = presenter()

        presenter.onAddTargetAmountChange(BigDecimal("4"))
        presenter.onAddBaseAmountChange(BigDecimal("7"))
        settle()

        assertEquals(0, BigDecimal("4").compareTo(state.value.targetAmountInputViewState.tokenAmount))
    }

    @Test
    fun `review opens confirmation with both amounts and the yield`() = runTest {
        val (presenter, _) = presenter()
        presenter.onAddBaseAmountChange(BigDecimal.ONE)
        settle()

        presenter.onAddReviewClick()
        settle()

        verify {
            router.openAddLiquidityConfirmScreen(
                ids,
                BigDecimal.ONE,
                match { it.compareTo(BigDecimal("2")) == 0 },
                BigDecimal("12.5").formatPercent() + "%"
            )
        }
    }

    @Test
    fun `review with too little balance shows a validation error instead`() = runTest {
        targetBalance = walletAsset(valToken, free = BigDecimal("1"))
        val (presenter, _) = presenter()
        presenter.onAddBaseAmountChange(BigDecimal.ONE)
        settle()

        presenter.onAddReviewClick()
        settle()

        verify { router.openErrorsScreen(any(), any()) }
        verify(exactly = 0) { router.openAddLiquidityConfirmScreen(any(), any(), any(), any()) }
    }

    @Test
    fun `review stops with the reason when liquidity is switched off`() = runTest {
        val (presenter, state) = presenter()
        presenter.onAddBaseAmountChange(BigDecimal.ONE)
        settle()
        coEvery { pools.mutationCapabilityReason(LiquidityMutationAction.Add) } returns "Liquidity paused"

        presenter.onAddReviewClick()
        settle()

        assertEquals("Liquidity paused", state.value.capabilityReason)
        assertFalse(state.value.buttonEnabled)
        assertFalse(state.value.buttonLoading)
        verify(exactly = 0) { router.openAddLiquidityConfirmScreen(any(), any(), any(), any()) }
    }

    @Test
    fun `without the target balance the inputs stay empty and info items still open`() = runTest {
        coEvery { wallet.getCurrentAssetOrNull(soraMainChainId, "val") } returns null
        val (presenter, state) = presenter()

        assertEquals(AmountInputViewState.defaultObj, state.value.baseAmountInputViewState)
        presenter.onAddBaseAmountChange(BigDecimal.TEN)
        presenter.resetInputFields()
        settle()
        assertEquals(AmountInputViewState.defaultObj, state.value.targetAmountInputViewState)
        presenter.onAddTableItemClick(2)
        verify { router.openInfoScreen(2) }
        assertNull(state.value.capabilityReason)
    }

    @Test
    fun `reset after a calculation clears both inputs`() = runTest {
        val (presenter, state) = presenter()
        presenter.onAddBaseAmountChange(BigDecimal.ONE)
        settle()

        presenter.resetInputFields()
        runCurrent()

        assertEquals(0, BigDecimal.ZERO.compareTo(state.value.baseAmountInputViewState.tokenAmount))
        assertEquals(0, BigDecimal.ZERO.compareTo(state.value.targetAmountInputViewState.tokenAmount))
    }

    private fun TestScope.settle() {
        advanceTimeBy(2_000)
        runCurrent()
    }

    private companion object {
        val FEE = BigDecimal("0.01")
    }
}

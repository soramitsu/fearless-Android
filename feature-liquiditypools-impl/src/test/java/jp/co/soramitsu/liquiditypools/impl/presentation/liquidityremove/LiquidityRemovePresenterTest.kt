package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove

import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.liquiditypools.domain.LiquidityMutationAction
import jp.co.soramitsu.liquiditypools.domain.interfaces.DemeterFarmingInteractor
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.domain.model.UserPoolData
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.VAL_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.XOR_ID
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.pswap
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.token
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.walletAsset
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.usecase.ValidateRemoveLiquidityUseCase
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
class LiquidityRemovePresenterTest {

    private val ids = StringPair(XOR_ID, VAL_ID)
    private val routes = MutableSharedFlow<LiquidityPoolsNavGraphRoute>(replay = 1)
    private val router = mockk<InternalPoolsRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns routes
    }
    private val chain = mockk<Chain>(relaxed = true) {
        every { assets } returns listOf(xor, valToken)
    }
    private val pool = CommonPoolData(
        basic = BasicPoolData(xor, valToken, BigDecimal("1000"), BigDecimal("2000"), BigDecimal("300"), "reserve"),
        user = UserPoolData(BigDecimal("100"), BigDecimal("200"), 10.0, BigDecimal("30"))
    )
    private val pools = mockk<PoolsInteractor> {
        every { poolsChainId } returns soraMainChainId
        coEvery { getPoolData(XOR_ID, VAL_ID) } returns flowOf(pool)
        coEvery { calcRemoveLiquidityNetworkFee(xor, valToken) } returns FEE
        coEvery { mutationCapabilityReason(LiquidityMutationAction.Remove) } returns null
    }
    private val wallet = mockk<WalletInteractor> {
        coEvery { getToken(xor) } returns token(xor)
        coEvery { getToken(valToken) } returns token(valToken)
        every { assetFlow(soraMainChainId, "xor") } answers { flowOf(walletAsset(xor, free = utilityFree)) }
    }
    private var utilityFree = BigDecimal("100")
    private val chains = mockk<ChainsRepository> { coEvery { getChain(soraMainChainId) } returns chain }
    private val demeter = mockk<DemeterFarmingInteractor> {
        coEvery { getFarmedPools(soraMainChainId) } returns null
    }
    private val resources = mockk<ResourceManager>(relaxed = true) {
        every { getString(R.string.common_available_format, *anyVararg()) } answers {
            "Available " + (args[1] as Array<*>).joinToString()
        }
    }

    private fun TestScope.presenter(): Pair<LiquidityRemovePresenter, StateFlow<LiquidityRemoveState>> {
        val store = mockk<CoroutinesStore> {
            every { uiScope } returns backgroundScope
            every { ioScope } returns backgroundScope
        }
        val presenter = LiquidityRemovePresenter(store, router, wallet, chains, pools, demeter, resources, ValidateRemoveLiquidityUseCase())
        val state = presenter.createScreenStateFlow(backgroundScope)
        routes.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen(ids))
        settle()
        return presenter to state
    }

    @Test
    fun `opened position shows pooled amounts of both tokens and the fee`() = runTest {
        val (_, state) = presenter()

        val value = state.value
        assertEquals("XOR", value.baseAmountInputViewState.tokenName)
        assertEquals("VAL", value.targetAmountInputViewState.tokenName)
        assertTrue(value.baseAmountInputViewState.totalBalance.contains("XOR"))
        assertTrue(value.targetAmountInputViewState.totalBalance.contains("VAL"))
        assertEquals(FEE.formatCryptoDetail("XOR"), value.feeInfo.feeAmount)
        assertFalse(value.buttonEnabled)
    }

    @Test
    fun `half of the base amount withdraws half of the target and half of the pool tokens`() = runTest {
        val (presenter, state) = presenter()

        presenter.onRemoveBaseAmountChange(BigDecimal("50"))
        settle()

        assertAmount("100", state.value.targetAmountInputViewState.tokenAmount)
        assertTrue(state.value.buttonEnabled)
        presenter.onRemoveReviewClick()
        settle()
        verify {
            router.openRemoveLiquidityConfirmScreen(
                ids,
                amount("50"),
                amount("100"),
                amount("49.75"),
                amount("99.5"),
                amount("15")
            )
        }
        assertFalse(state.value.buttonLoading)
    }

    @Test
    fun `a target amount above the position is capped and withdraws every pool token`() = runTest {
        val (presenter, state) = presenter()

        presenter.onRemoveTargetAmountChange(BigDecimal("300"))
        settle()

        assertAmount("100", state.value.baseAmountInputViewState.tokenAmount)
        assertAmount("200", state.value.targetAmountInputViewState.tokenAmount)
        presenter.onRemoveReviewClick()
        settle()
        verify { router.openRemoveLiquidityConfirmScreen(ids, amount("100"), amount("200"), any(), any(), amount("30")) }
    }

    @Test
    fun `tokens deposited in a Demeter farm cannot be withdrawn`() = runTest {
        coEvery { demeter.getFarmedPools(soraMainChainId) } returns listOf(
            DemeterFarmingPool(walletAsset(xor), walletAsset(valToken), walletAsset(pswap), 10.0, BigDecimal("15"), BigDecimal.ONE)
        )
        val (presenter, state) = presenter()

        presenter.onRemoveBaseAmountChange(BigDecimal("80"))
        settle()

        assertAmount("50", state.value.baseAmountInputViewState.tokenAmount)
        assertAmount("100", state.value.targetAmountInputViewState.tokenAmount)
    }

    @Test
    fun `too little XOR for the fee shows a validation error`() = runTest {
        utilityFree = BigDecimal("0.001")
        val (presenter, _) = presenter()
        presenter.onRemoveBaseAmountChange(BigDecimal("50"))
        settle()

        presenter.onRemoveReviewClick()
        settle()

        verify { router.openErrorsScreen(any(), any()) }
        verify(exactly = 0) { router.openRemoveLiquidityConfirmScreen(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `switched off removal explains the reason and does not open confirmation`() = runTest {
        coEvery { pools.mutationCapabilityReason(LiquidityMutationAction.Remove) } returns "Removal paused"
        val (presenter, state) = presenter()
        presenter.onRemoveBaseAmountChange(BigDecimal("50"))
        settle()

        presenter.onRemoveReviewClick()
        settle()

        assertEquals("Removal paused", state.value.capabilityReason)
        assertFalse(state.value.buttonEnabled)
        verify(exactly = 0) { router.openRemoveLiquidityConfirmScreen(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a pool without an account position offers nothing to withdraw`() = runTest {
        coEvery { pools.getPoolData(XOR_ID, VAL_ID) } returns flowOf(pool.copy(user = null))
        val (presenter, state) = presenter()

        presenter.onRemoveBaseAmountChange(BigDecimal("50"))
        settle()
        presenter.onRemoveReviewClick()
        settle()

        assertAmount("0", state.value.baseAmountInputViewState.tokenAmount)
        assertFalse(state.value.buttonEnabled)
        verify(exactly = 0) { router.openRemoveLiquidityConfirmScreen(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { router.openErrorsScreen(any(), any()) }
    }

    @Test
    fun `focus and information callbacks update the screen and router`() = runTest {
        val (presenter, state) = presenter()

        presenter.onRemoveBaseAmountFocusChange(true)
        presenter.onRemoveTargetAmountFocusChange(true)
        presenter.onRemoveItemClick(2)
        runCurrent()

        assertTrue(state.value.baseAmountInputViewState.isFocused)
        assertTrue(state.value.targetAmountInputViewState.isFocused)
        verify { router.openInfoScreen(2) }
    }

    private fun MockKMatcherScope.amount(value: String): BigDecimal = match { it.compareTo(BigDecimal(value)) == 0 }

    private fun TestScope.settle() {
        advanceTimeBy(2_000)
        runCurrent()
    }

    private fun assertAmount(expected: String, actual: BigDecimal) {
        assertTrue("expected $expected but was $actual", BigDecimal(expected).compareTo(actual) == 0)
    }

    private companion object {
        val FEE = BigDecimal("0.01")
    }
}

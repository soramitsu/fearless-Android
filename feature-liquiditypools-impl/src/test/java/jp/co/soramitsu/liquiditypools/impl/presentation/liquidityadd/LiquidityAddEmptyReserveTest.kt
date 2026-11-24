package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.liquiditypools.domain.LiquidityMutationAction
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.USDX_ID
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.XOR_ID
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.usdx
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.walletAsset
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.xor
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
import org.junit.Test
import java.math.BigDecimal

/**
 * Bug 32: calculateAmount checked `reservesSecond.isZero()` twice and never the base reserve, so a pool with
 * no base reserve priced the counterpart amount at zero instead of leaving both amounts to the user.
 * The target asset (USDX) has 6 decimals.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiquidityAddEmptyReserveTest {

    private val ids = StringPair(XOR_ID, USDX_ID)
    private val routes = MutableSharedFlow<LiquidityPoolsNavGraphRoute>(replay = 1)
    private val router = mockk<InternalPoolsRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns routes
    }
    private val chain = mockk<Chain>(relaxed = true) {
        every { id } returns soraMainChainId
        every { ecosystem } returns Ecosystem.Substrate
        every { addressPrefix } returns 69
        every { assets } returns listOf(xor, usdx)
        every { externalApi } returns null
    }
    private var reserves = pool(base = "1000", target = "2000")
    private val pools = mockk<PoolsInteractor> {
        every { poolsChainId } returns soraMainChainId
        coEvery { isPairEnabled(XOR_ID, USDX_ID) } returns true
        coEvery { getPoolData(XOR_ID, USDX_ID) } answers { flowOf(CommonPoolData(reserves, null)) }
        coEvery { mutationCapabilityReason(LiquidityMutationAction.Add) } returns null
        coEvery { getSbApy(any()) } returns null
        coEvery { calcAddLiquidityNetworkFee(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            BigDecimal("0.007")
    }
    private val wallet = mockk<WalletInteractor> {
        coEvery { getCurrentAssetOrNull(soraMainChainId, "xor") } returns walletAsset(xor, BigDecimal("2"))
        coEvery { getCurrentAssetOrNull(soraMainChainId, "usdx") } returns walletAsset(usdx, BigDecimal.ONE)
        coEvery { getChain(soraMainChainId) } returns chain
        every { assetFlow(soraMainChainId, "xor") } returns flowOf(walletAsset(xor, BigDecimal("2")))
    }
    private val chains = mockk<ChainsRepository> { coEvery { getChain(soraMainChainId) } returns chain }
    private val accounts = mockk<AccountInteractor> { coEvery { selectedMetaAccount() } returns mockk(relaxed = true) }
    private val resources = mockk<ResourceManager>(relaxed = true)

    @Test
    fun shouldKeepBothEnteredAmounts_whenThePoolHasNoBaseReserve() = runTest {
        reserves = pool(base = "0", target = "2000")
        val (presenter, state) = openAddLiquidity()

        presenter.onAddBaseAmountFocusChange(true)
        presenter.onAddBaseAmountChange(BigDecimal("5"))
        settle()
        presenter.onAddTargetAmountFocusChange(true)
        presenter.onAddTargetAmountChange(BigDecimal("7"))
        settle()

        assertAmount("5", state.value.baseAmountInputViewState.tokenAmount)
        assertAmount("7", state.value.targetAmountInputViewState.tokenAmount)
    }

    @Test
    fun shouldKeepBothEnteredAmounts_whenThePoolHasNoTargetReserve() = runTest {
        reserves = pool(base = "1000", target = "0")
        val (presenter, state) = openAddLiquidity()

        presenter.onAddTargetAmountFocusChange(true)
        presenter.onAddTargetAmountChange(BigDecimal("7"))
        settle()
        presenter.onAddBaseAmountFocusChange(true)
        presenter.onAddBaseAmountChange(BigDecimal("5"))
        settle()

        assertAmount("5", state.value.baseAmountInputViewState.tokenAmount)
        assertAmount("7", state.value.targetAmountInputViewState.tokenAmount)
    }

    @Test
    fun shouldFillTheOtherAmountAtThePoolRatio_whenBothReservesArePresent() = runTest {
        val (presenter, state) = openAddLiquidity()

        presenter.onAddBaseAmountFocusChange(true)
        presenter.onAddBaseAmountChange(BigDecimal("1.5"))
        settle()
        assertAmount("3", state.value.targetAmountInputViewState.tokenAmount)

        presenter.onAddTargetAmountFocusChange(true)
        presenter.onAddTargetAmountChange(BigDecimal("0.000001"))
        settle()
        // 1 plank of USDX buys 0.0000005 XOR at 1000 XOR : 2000 USDX.
        assertAmount("0.0000005", state.value.baseAmountInputViewState.tokenAmount)
    }

    private fun TestScope.openAddLiquidity(): Pair<LiquidityAddPresenter, StateFlow<LiquidityAddState>> {
        val store = mockk<CoroutinesStore> {
            every { uiScope } returns backgroundScope
            every { ioScope } returns backgroundScope
        }
        val presenter = LiquidityAddPresenter(
            store,
            router,
            wallet,
            chains,
            pools,
            accounts,
            resources,
            ValidateAddLiquidityUseCase()
        )
        val state = presenter.createScreenStateFlow(backgroundScope)
        backgroundScope.launch { state.collect {} }
        routes.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityAddScreen(ids))
        settle()
        return presenter to state
    }

    private fun pool(base: String, target: String) = BasicPoolData(
        baseToken = xor,
        targetToken = usdx,
        baseReserves = BigDecimal(base),
        targetReserves = BigDecimal(target),
        totalIssuance = BigDecimal("100"),
        reserveAccount = "cnReserveAccount"
    )

    private fun assertAmount(expected: String, actual: BigDecimal) {
        assertEquals("expected $expected but was ${actual.toPlainString()}", 0, BigDecimal(expected).compareTo(actual))
    }

    // Runs the presenter's work, debounces included; backgroundScope work does not keep advanceUntilIdle going.
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MILLIS)
        runCurrent()
    }

    private companion object {
        const val SETTLE_MILLIS = 10_000L
    }
}

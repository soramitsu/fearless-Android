package jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.formatPercent
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.domain.model.UserPoolData
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.USDX_ID
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.XOR_ID
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.token
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.usdx
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.xor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
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
import org.junit.Test
import java.math.BigDecimal

/**
 * Bug 30: the pool details screen showed "null%" as the APY of a pool without a strategic-bonus yield, and
 * valued the pooled target amount at the base token's price.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PoolDetailsYieldAndValueTest {

    private val ids = StringPair(XOR_ID, USDX_ID)
    private val routes = MutableSharedFlow<LiquidityPoolsNavGraphRoute>(replay = 1)
    private val router = mockk<InternalPoolsRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns routes
    }
    private val pool = CommonPoolData(
        basic = BasicPoolData(
            baseToken = xor,
            targetToken = usdx,
            baseReserves = BigDecimal("1000"),
            targetReserves = BigDecimal("2000"),
            totalIssuance = BigDecimal("100"),
            reserveAccount = RESERVE_ACCOUNT
        ),
        user = UserPoolData(
            basePooled = BigDecimal("10"),
            targetPooled = BigDecimal("20"),
            poolShare = 1.0,
            poolProvidersBalance = BigDecimal("1")
        )
    )
    private val poolsInteractor = mockk<PoolsInteractor> {
        coEvery { getPoolData(XOR_ID, USDX_ID) } returns flowOf(pool)
    }
    private val walletInteractor = mockk<WalletInteractor> {
        coEvery { getToken(xor) } returns token(xor, XOR_PRICE)
        coEvery { getToken(usdx) } returns token(usdx, USDX_PRICE)
    }

    @Test
    fun shouldShowNoPercentSign_whenThePoolHasNoStrategicBonusYield() = runTest {
        coEvery { poolsInteractor.getSbApy(RESERVE_ACCOUNT) } returns null

        val state = openDetails()

        assertEquals("", state.value.apy)
    }

    @Test
    fun shouldShowTheYield_whenThePoolHasAStrategicBonusYield() = runTest {
        coEvery { poolsInteractor.getSbApy(RESERVE_ACCOUNT) } returns 12.5

        val state = openDetails()

        assertEquals("${BigDecimal("12.5").formatPercent()}%", state.value.apy)
    }

    @Test
    fun shouldValueEachPooledAmountAtItsOwnTokenPrice() = runTest {
        coEvery { poolsInteractor.getSbApy(RESERVE_ACCOUNT) } returns null

        val state = openDetails()

        // 10 XOR at $2 and 20 USDX at $1.
        assertEquals(BigDecimal("20").formatFiat("$"), state.value.pooledBaseFiat)
        assertEquals(BigDecimal("20").formatFiat("$"), state.value.pooledTargetFiat)
        // TVL is twice the base side: 2 x 1000 XOR at $2.
        assertEquals(BigDecimal("4000").formatFiat("$"), state.value.tvl)
    }

    private fun TestScope.openDetails(): StateFlow<PoolDetailsState> {
        val store = mockk<CoroutinesStore> {
            every { uiScope } returns backgroundScope
            every { ioScope } returns backgroundScope
        }
        val presenter = PoolDetailsPresenter(
            coroutinesStore = store,
            internalPoolsRouter = router,
            walletInteractor = walletInteractor,
            poolsInteractor = poolsInteractor,
            accountInteractor = mockk<AccountInteractor>()
        )
        val state = presenter.createScreenStateFlow(backgroundScope)
        routes.tryEmit(LiquidityPoolsNavGraphRoute.PoolDetailsScreen(ids))
        settle()
        return state
    }

    // Runs the presenter's work, debounces included; backgroundScope work does not keep advanceUntilIdle going.
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MILLIS)
        runCurrent()
    }

    private companion object {
        const val SETTLE_MILLIS = 10_000L
        const val RESERVE_ACCOUNT = "cnReserveAccount"
        val XOR_PRICE = BigDecimal("2")
        val USDX_PRICE = BigDecimal("1")
    }
}

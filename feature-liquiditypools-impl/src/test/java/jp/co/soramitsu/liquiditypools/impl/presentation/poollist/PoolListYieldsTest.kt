package jp.co.soramitsu.liquiditypools.impl.presentation.poollist

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.USDX_ID
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.VAL_ID
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.XOR_ID
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.token
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.usdx
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.valToken
import jp.co.soramitsu.liquiditypools.impl.PoolsPrecisionFixtures.xor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.presentation.allpools.BasicPoolListItemState
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Bug 34: the pool list built its rows and applied the strategic-bonus yields in two independent collectors.
 * Rows rebuilt by a search, or built after the yields arrived, kept a loading APY for good.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PoolListYieldsTest {

    private val routes = MutableSharedFlow<LiquidityPoolsNavGraphRoute>(replay = 1)
    private val router = mockk<InternalPoolsRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns routes
    }
    private val pools = mockk<PoolsInteractor> {
        every { subscribePoolsCacheCurrentAccount() } returns flowOf(
            listOf(pool(valToken, "valReserve"), pool(usdx, "usdxReserve"))
        )
        coEvery { getSbApy("valReserve") } returns 10.0
        coEvery { getSbApy("usdxReserve") } returns 20.5
    }
    private var tokenDelayMillis = 0L
    private val wallet = mockk<WalletInteractor> {
        coEvery { getToken(xor) } coAnswers {
            delay(tokenDelayMillis)
            token(xor, BigDecimal("2"))
        }
    }

    @Test
    fun shouldShowEveryYield_whenTheListOpens() = runTest {
        val state = openList()

        assertEquals(apy(10.0), state.value.row(VAL_ID).apy.loadedText())
        assertEquals(apy(20.5), state.value.row(USDX_ID).apy.loadedText())
    }

    @Test
    fun shouldKeepTheYields_whenTheSearchQueryChanges() = runTest {
        val presenter = presenter()
        val state = openList(presenter)

        presenter.onAssetSearchEntered("usdx")
        settle()

        assertEquals(listOf(USDX_ID), state.value.pools.map { it.ids.second })
        assertEquals(apy(20.5), state.value.row(USDX_ID).apy.loadedText())

        presenter.onAssetSearchEntered("")
        settle()

        assertEquals(apy(10.0), state.value.row(VAL_ID).apy.loadedText())
        assertEquals(apy(20.5), state.value.row(USDX_ID).apy.loadedText())
    }

    @Test
    fun shouldShowEveryYield_whenTheRowsAreBuiltAfterTheYieldsArrive() = runTest {
        // Pricing the rows takes longer than reading the yields.
        tokenDelayMillis = 1_000

        val state = openList()

        assertEquals(apy(10.0), state.value.row(VAL_ID).apy.loadedText())
        assertEquals(apy(20.5), state.value.row(USDX_ID).apy.loadedText())
    }

    @Test
    fun shouldStopLoadingTheApy_whenAPoolHasNoStrategicBonusYield() = runTest {
        coEvery { pools.getSbApy("usdxReserve") } returns null

        val state = openList()

        assertNull(state.value.row(USDX_ID).apy)
        assertEquals(apy(10.0), state.value.row(VAL_ID).apy.loadedText())
    }

    private fun TestScope.presenter(): PoolListPresenter {
        val store = mockk<CoroutinesStore> {
            every { uiScope } returns backgroundScope
            every { ioScope } returns backgroundScope
        }
        return PoolListPresenter(store, router, wallet, pools)
    }

    private fun TestScope.openList(presenter: PoolListPresenter = presenter()): StateFlow<PoolListState> {
        val state = presenter.createScreenStateFlow(backgroundScope)
        routes.tryEmit(LiquidityPoolsNavGraphRoute.ListPoolsScreen(isUserPools = false))
        settle()
        return state
    }

    private fun PoolListState.row(targetId: String): BasicPoolListItemState =
        pools.single { it.ids == StringPair(XOR_ID, targetId) }

    private fun LoadingState<String>?.loadedText(): String? = (this as? LoadingState.Loaded)?.data

    private fun apy(value: Double) = "%s%%".format(value.toBigDecimal().formatCrypto())

    private fun pool(target: jp.co.soramitsu.core.models.Asset, reserveAccount: String) = CommonPoolData(
        basic = BasicPoolData(
            baseToken = xor,
            targetToken = target,
            baseReserves = BigDecimal("1000"),
            targetReserves = BigDecimal("2000"),
            totalIssuance = BigDecimal("100"),
            reserveAccount = reserveAccount
        ),
        user = null
    )

    // Runs the presenter's work, debounces included; backgroundScope work does not keep advanceUntilIdle going.
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MILLIS)
        runCurrent()
    }

    private companion object {
        const val SETTLE_MILLIS = 10_000L
    }
}

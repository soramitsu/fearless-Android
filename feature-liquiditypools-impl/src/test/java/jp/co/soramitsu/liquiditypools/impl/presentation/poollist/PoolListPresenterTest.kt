package jp.co.soramitsu.liquiditypools.impl.presentation.poollist

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.BasicPoolData
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.domain.model.UserPoolData
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.coreAsset
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.token
import jp.co.soramitsu.liquiditypools.impl.LiquidityFixtures.xor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class PoolListPresenterTest {

    private val routes = MutableSharedFlow<LiquidityPoolsNavGraphRoute>(replay = 1)
    private val router = mockk<InternalPoolsRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns routes
    }
    private val wallet = mockk<WalletInteractor> { coEvery { getToken(any()) } answers { token(firstArg()) } }
    private val pools = mockk<PoolsInteractor> {
        every { subscribePoolsCacheCurrentAccount() } returns flowOf(
            listOf(pool("val", 100, "5"), pool("dai", 300, null), pool("pswap", 200, "9"), pool("eth", 50, null))
        )
        // Yields arrive after the list, as from the block explorer.
        coEvery { getSbApy(any()) } coAnswers {
            delay(100)
            if (firstArg<String>() == "r-eth") null else 7.25
        }
    }

    private fun TestScope.presenter(isUserPools: Boolean): Pair<PoolListPresenter, StateFlow<PoolListState>> {
        val store = mockk<CoroutinesStore> { every { uiScope } returns backgroundScope }
        val presenter = PoolListPresenter(store, router, wallet, pools)
        val state = presenter.createScreenStateFlow(backgroundScope)
        routes.tryEmit(LiquidityPoolsNavGraphRoute.ListPoolsScreen(isUserPools))
        settle()
        return presenter to state
    }

    @Test
    fun `available pools are listed by liquidity with their yields`() = runTest {
        val (_, state) = presenter(isUserPools = false)

        val value = state.value
        assertFalse(value.isLoading)
        // TVL orders the list; two account positions compare by pooled value (PSWAP 9 before VAL 5).
        assertEquals(listOf("XOR-DAI", "XOR-PSWAP", "XOR-VAL", "XOR-ETH"), value.pools.map { it.text1 })
        val apy = "%s%%".format(BigDecimal("7.25").formatCrypto())
        assertEquals(apy, (value.pools.first().apy as LoadingState.Loaded).data)
        assertTrue("no yield published for ETH", value.pools.last().apy is LoadingState.Loading)
    }

    @Test
    fun `account pools only list positions, ordered by pooled value`() = runTest {
        val (_, state) = presenter(isUserPools = true)

        assertEquals(listOf("XOR-PSWAP", "XOR-VAL"), state.value.pools.map { it.text1 })
    }

    @Test
    fun `search filters by symbol and is shown in the field`() = runTest {
        val (presenter, state) = presenter(isUserPools = false)

        presenter.onAssetSearchEntered("dai")
        settle()

        assertEquals("dai", state.value.searchQuery)
        assertEquals(listOf("XOR-DAI"), state.value.pools.map { it.text1 })
        presenter.onPoolClicked(StringPair("0xa", "0xb"))
        verify { router.openDetailsPoolScreen(StringPair("0xa", "0xb")) }
    }

    @Test
    fun `screen starts loading until pools arrive`() = runTest {
        val store = mockk<CoroutinesStore> { every { uiScope } returns backgroundScope }
        val state = PoolListPresenter(store, router, wallet, pools).createScreenStateFlow(backgroundScope)

        runCurrent()

        assertTrue(state.value.isLoading)
        assertTrue(state.value.pools.isEmpty())
    }

    private fun TestScope.settle() {
        advanceTimeBy(500)
        runCurrent()
    }

    private fun pool(
        symbol: String,
        reserves: Int,
        basePooled: String?
    ) = CommonPoolData(
        basic = BasicPoolData(
            xor, coreAsset(symbol, symbol.uppercase(), "0x$symbol"), BigDecimal(reserves), BigDecimal(reserves),
            BigDecimal.TEN, "r-$symbol"
        ),
        user = basePooled?.let { UserPoolData(BigDecimal(it), BigDecimal(it), 1.0, BigDecimal.ONE) }
    )
}

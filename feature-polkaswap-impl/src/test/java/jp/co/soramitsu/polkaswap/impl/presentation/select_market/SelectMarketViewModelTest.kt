package jp.co.soramitsu.polkaswap.impl.presentation.select_market

import androidx.lifecycle.viewModelScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectMarketViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<PolkaswapRouter>(relaxed = true)
    private val bestDex = MutableStateFlow<LoadingState<Int>>(LoadingState.Loading())
    private val interactor = mockk<PolkaswapInteractor> {
        every { bestDexIdFlow } returns bestDex
        every { availableMarkets } returns mutableMapOf(1 to listOf(Market.SMART, Market.XYK))
    }
    private lateinit var vm: SelectMarketViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = SelectMarketViewModel(router, interactor)
    }

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun marketsFollowTheBestDexAndFallBackToSmart() = runTest(dispatcher) {
        runCurrent()
        assertTrue(vm.state.value is LoadingState.Loading)

        bestDex.value = LoadingState.Loaded(1); runCurrent()
        assertEquals(listOf(Market.SMART, Market.XYK), (vm.state.value as LoadingState.Loaded).data)

        bestDex.value = LoadingState.Loaded(7); runCurrent()
        assertEquals(listOf(Market.SMART), (vm.state.value as LoadingState.Loaded).data)
    }

    @Test fun selectingAMarketReturnsItToTheSettings() {
        vm.marketSelected(Market.TBC)

        verify { router.backWithResult(SelectMarketFragment.MARKET_KEY to Market.TBC) }
    }
}

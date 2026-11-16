package jp.co.soramitsu.polkaswap.impl.presentation.select_market

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.impl.FragmentHiltShim
import jp.co.soramitsu.polkaswap.impl.ShimApplication
import jp.co.soramitsu.polkaswap.impl.idleMainUntil
import jp.co.soramitsu.polkaswap.impl.launchHost
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = ShimApplication::class)
class SelectMarketFragmentTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val router = mockk<PolkaswapRouter>(relaxed = true)
    private val interactor = mockk<PolkaswapInteractor> {
        every { bestDexIdFlow } returns MutableStateFlow(LoadingState.Loaded(0))
        every { availableMarkets } returns mutableMapOf(0 to listOf(Market.SMART, Market.XYK))
    }

    @After fun tearDown() = FragmentHiltShim.reset()

    @Test fun choosingAMarketReturnsIt() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        FragmentHiltShim.viewModel(SelectMarketViewModel::class) { SelectMarketViewModel(router, interactor) }
        val host = launchHost()
        val fragment = SelectMarketFragment()
        host.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        idleMainUntil { fragment.view != null }

        compose.onNodeWithText(Market.XYK.marketName).performClick()

        verify { router.backWithResult(SelectMarketFragment.MARKET_KEY to Market.XYK) }
    }
}

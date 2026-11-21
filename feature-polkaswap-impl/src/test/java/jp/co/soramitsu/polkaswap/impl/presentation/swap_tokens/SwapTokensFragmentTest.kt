package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.presentation.InfoDialog
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.impl.FragmentHiltShim
import jp.co.soramitsu.polkaswap.impl.ShimApplication
import jp.co.soramitsu.polkaswap.impl.idleMainUntil
import jp.co.soramitsu.polkaswap.impl.launchHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = ShimApplication::class)
class SwapTokensFragmentTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resources = mockk<ResourceManager> {
        every { getString(any()) } answers { "s:${firstArg<Int>()}" }
        every { getString(any(), *anyVararg()) } answers { "f:${firstArg<Int>()}" }
    }
    private val interactor = mockk<PolkaswapInteractor>(relaxed = true) {
        every { hasReadDisclaimer } returns true
        every { observeHasReadDisclaimer() } returns flowOf(true)
        every { bestDexIdFlow } returns MutableStateFlow(LoadingState.Loaded(0))
        every { polkaswapChainId } returns "sora"
        coEvery { getAvailableDexes() } returns emptyList()
    }
    private val router = mockk<PolkaswapRouter>(relaxed = true) { every { observeResult<Any>(any()) } returns emptyFlow() }
    private lateinit var fragment: SwapTokensFragment

    @Before fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        FragmentHiltShim.viewModel(SwapTokensViewModel::class) { handle ->
            SwapTokensViewModel(resources, interactor, router, mockk(relaxed = true), handle)
        }
        val host = launchHost()
        fragment = SwapTokensFragment().apply { arguments = SwapTokensFragment.getBundle("sora", null, null) }
        host.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        idleMainUntil { fragment.view != null }
    }

    @After fun tearDown() = FragmentHiltShim.reset()

    @Test fun marketSettingsWithoutAssetsWarnInADialog() {
        compose.onNodeWithText(context.getString(R.string.common_preview)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_swap_settings)).performClick()

        idleMainUntil { fragment.childFragmentManager.fragments.any { it is ErrorDialog } }
    }

    @Test fun tooltipsOpenAnInfoDialog() {
        fragment.viewModel.networkFeeTooltipClick()

        idleMainUntil { fragment.childFragmentManager.fragments.any { it is InfoDialog } }
    }

    @Test fun layoutChangesReportWhetherTheKeyboardIsOpen() {
        fragment.viewModel.setSoftKeyboardOpen(true)

        fragment.requireView().viewTreeObserver.dispatchOnGlobalLayout()

        assertFalse(fragment.viewModel.isSoftKeyboardOpenFlow.value)
    }
}

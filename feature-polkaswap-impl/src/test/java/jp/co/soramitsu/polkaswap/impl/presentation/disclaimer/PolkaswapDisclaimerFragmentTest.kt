package jp.co.soramitsu.polkaswap.impl.presentation.disclaimer

import android.content.Context
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.impl.FragmentHiltShim
import jp.co.soramitsu.polkaswap.impl.ShimApplication
import jp.co.soramitsu.polkaswap.impl.idleMainUntil
import jp.co.soramitsu.polkaswap.impl.launchHost
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
class PolkaswapDisclaimerFragmentTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val router = mockk<PolkaswapRouter>(relaxed = true)
    private val interactor = mockk<PolkaswapInteractor>(relaxed = true) { every { hasReadDisclaimer } returns false }
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "s:${firstArg<Int>()}" } }

    @After fun tearDown() = FragmentHiltShim.reset()

    @Test fun acceptingTheTermsAnswersTheRequestingScreen() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        FragmentHiltShim.viewModel(PolkaswapDisclaimerViewModel::class) { handle ->
            PolkaswapDisclaimerViewModel(resources, interactor, router, handle)
        }
        val host = launchHost()
        val fragment = PolkaswapDisclaimerFragment().apply { arguments = PolkaswapDisclaimerFragment.getBundle(7) }
        host.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        idleMainUntil { fragment.view != null }

        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.common_continue)).performScrollTo().performClick()

        verify { interactor.hasReadDisclaimer = true }
        verify { router.backWithResult(7, PolkaswapDisclaimerFragment.KEY_DISCLAIMER_READ_RESULT to true) }
    }
}

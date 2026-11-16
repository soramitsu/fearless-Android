package jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel
import jp.co.soramitsu.polkaswap.impl.FragmentHiltShim
import jp.co.soramitsu.polkaswap.impl.ShimApplication
import jp.co.soramitsu.polkaswap.impl.idleMainUntil
import jp.co.soramitsu.polkaswap.impl.launchHost
import kotlinx.coroutines.flow.emptyFlow
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
class TransactionSettingsFragmentTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val router = mockk<PolkaswapRouter>(relaxed = true) { every { observeResult<Market>(any()) } returns emptyFlow() }
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "s:${firstArg<Int>()}" } }

    @After fun tearDown() = FragmentHiltShim.reset()

    @Test fun settingsFromTheArgumentsAreShownAndSavedBack() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        FragmentHiltShim.viewModel(TransactionSettingsViewModel::class) { handle -> TransactionSettingsViewModel(router, resources, handle) }
        val host = launchHost()
        val settings = TransactionSettingsModel(Market.XYK, 1.0)
        val fragment = TransactionSettingsFragment().apply { arguments = TransactionSettingsFragment.getBundle(settings) }
        host.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        idleMainUntil { fragment.view != null }

        compose.onNodeWithText(Market.XYK.marketName).assertExists()
        compose.onNodeWithText(context.getString(R.string.common_save)).performClick()

        verify { router.backWithResult(TransactionSettingsFragment.SETTINGS_MODEL_KEY to settings) }
    }
}

package jp.co.soramitsu.wallet.impl.presentation.manageassets

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The manage-assets sheet applies its opening filter, reports Done and reports its closing. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h2400dp")
class ManageAssetsFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val viewModel = mockk<ManageAssetsViewModel>(relaxed = true)
    private var host: ActivityController<StubHiltActivity>? = null

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
        every { viewModel.state } returns MutableStateFlow(ManageAssetsScreenViewState(assets = emptyMap(), showTestnets = false))
    }

    @After
    fun tearDown() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun openingFilterIsAppliedAndDoneIsReported() {
        val fragment = ManageAssetsFragment()
        fragment.arguments = bundleOf(FILTER_ARGUMENT to "Hidden")
        host = hostSheet(fragment)

        verify { viewModel.applyInitialFilter("Hidden") }
        compose.onNodeWithText(string(R.string.wallet_manage_assets)).assertExists()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.common_done)).performClick()
        settle()

        verify { viewModel.onDoneClicked() }
        assertTrue((fragment.dialog as BottomSheetDialog).behavior.skipCollapsed)
    }

    @Test
    fun withoutArgumentsTheDefaultFilterIsRequested() {
        host = hostSheet(ManageAssetsFragment())

        verify { viewModel.applyInitialFilter(null) }
    }

    @Test
    fun dismissingTheSheetTellsTheViewModel() {
        val fragment = ManageAssetsFragment()
        host = hostSheet(fragment)

        fragment.dismiss()
        ShadowLooper.idleMainLooper()

        verify { viewModel.onDialogClose() }
    }

    private fun hostSheet(fragment: ManageAssetsFragment): ActivityController<StubHiltActivity> {
        val controller = HiltFragmentHost.host(fragment, mapOf(ManageAssetsViewModel::class.java to viewModel))
        settle()
        return controller
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private companion object {
        const val FILTER_ARGUMENT = "manage_asset_filter"
    }
}

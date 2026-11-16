package jp.co.soramitsu.wallet.impl.presentation.balance.walletselector

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletSelectorViewState
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The wallet switcher sheet reports selection, options, adding and Google sign-in results to its view model. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h1600dp")
class SelectWalletFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val googleAuthorize = MutableLiveData<Event<Unit>>()
    private val viewModel = mockk<SelectWalletViewModel>(relaxed = true)
    private var host: ActivityController<StubHiltActivity>? = null

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
        val wallet = WalletItemViewState(
            id = WALLET_ID, balance = "10", assetSymbol = "$", title = WALLET_NAME,
            walletIcon = R.drawable.ic_wallet, isSelected = true
        )
        every { viewModel.state } returns MutableStateFlow(WalletSelectorViewState(listOf(wallet), wallet))
        every { viewModel.googleAuthorizeLiveData } returns googleAuthorize
        every { viewModel.importPreInstalledWalletLiveData } returns MutableLiveData<Event<Unit>>()
    }

    @After
    fun tearDown() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun sheetReportsSelectionOptionsAddingAndBack() {
        host = hostSheet()

        compose.onNodeWithText(WALLET_NAME).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithContentDescription(string(R.string.ux_more)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.common_add_new_wallet)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithContentDescription(string(R.string.ux_back)).performClick()
        settle()

        verify { viewModel.onWalletSelected(match { it.id == WALLET_ID }) }
        verify { viewModel.onWalletOptionsClick(match { it.id == WALLET_ID }) }
        verify { viewModel.addNewWallet() }
        verify { viewModel.onBackClicked() }
    }

    @Test
    fun withoutABrowserGoogleRequestsAreNotObserved() {
        host = hostSheet()

        googleAuthorize.value = Event(Unit)
        ShadowLooper.idleMainLooper()

        verify(exactly = 0) { viewModel.authorizeGoogle(any()) }
    }

    @Test
    fun googleSignInResultsReachTheViewModel() {
        allowHttpsLinks()
        host = hostSheet()

        googleAuthorize.value = Event(Unit)
        ShadowLooper.idleMainLooper()
        val launcher = slot<ActivityResultLauncher<Intent>>()
        verify { viewModel.authorizeGoogle(capture(launcher)) }

        deliverResult(launcher.captured, Intent("test.GOOGLE_SIGN_IN_OK"), Activity.RESULT_OK, Intent())
        verify { viewModel.openAddWalletThroughGoogleScreen() }

        deliverResult(
            launcher.captured,
            Intent("test.GOOGLE_SIGN_IN_FAILED"),
            Activity.RESULT_CANCELED,
            GoogleSignInResult.failureData(GoogleSignInResult.Failure.NETWORK)
        )
        verify { viewModel.onGoogleSignInFailed(GoogleSignInResult.Network) }
    }

    private fun deliverResult(launcher: ActivityResultLauncher<Intent>, request: Intent, resultCode: Int, data: Intent) {
        launcher.launch(request)
        ShadowLooper.idleMainLooper()
        val activity = host!!.get()
        val started = shadowOf(activity).nextStartedActivity
        shadowOf(activity).receiveResult(started, resultCode, data)
        ShadowLooper.idleMainLooper()
    }

    /** Lets the sheet see a browser for Google's https sign-in page. */
    private fun allowHttpsLinks() {
        val https = Intent(Intent.ACTION_VIEW, Uri.parse("https://accounts.google.com/")).addCategory(Intent.CATEGORY_BROWSABLE)
        val browser = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = BROWSER_PACKAGE
                name = "$BROWSER_PACKAGE.Main"
                applicationInfo = ApplicationInfo().apply { packageName = BROWSER_PACKAGE }
            }
        }
        shadowOf(ComposeFixtures.context.packageManager).addResolveInfoForIntent(https, browser)
    }

    private fun hostSheet(): ActivityController<StubHiltActivity> {
        val controller = HiltFragmentHost.host(SelectWalletFragment(), mapOf(SelectWalletViewModel::class.java to viewModel))
        settle()
        return controller
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private companion object {
        const val WALLET_ID = 7L
        const val WALLET_NAME = "Main wallet"
        const val BROWSER_PACKAGE = "com.example.browser"
    }
}

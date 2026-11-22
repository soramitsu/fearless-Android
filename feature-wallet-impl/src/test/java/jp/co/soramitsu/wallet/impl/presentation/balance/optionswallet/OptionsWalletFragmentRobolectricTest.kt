package jp.co.soramitsu.wallet.impl.presentation.balance.optionswallet

import android.widget.Button
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Outcome
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Row
import jp.co.soramitsu.account.api.domain.UniversalWalletSlot
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.PrimaryButton
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.balance.list.UniversalPasswordNoticeSheet
import jp.co.soramitsu.wallet.impl.presentation.balance.list.UniversalPasswordNoticeState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/** The wallet options sheet reports its buttons, confirms deletion and runs the old-address notice. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h1600dp")
class OptionsWalletFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val deleteConfirmation = MutableLiveData<Event<Long>>()
    private val passwordNotice = MutableLiveData<Event<UniversalPasswordNoticeState>>()
    private val passwordResult = MutableLiveData<Event<UniversalPasswordNoticeState>>()
    private val viewModel = mockk<OptionsWalletViewModel>(relaxed = true)
    private var host: ActivityController<StubHiltActivity>? = null
    private lateinit var fragment: OptionsWalletFragment

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
        every { viewModel.state } returns MutableStateFlow(
            OptionsWalletScreenViewState(isSelected = false, showDetailsButton = true, showOldAddressesButton = true)
        )
        every { viewModel.deleteWalletConfirmation } returns deleteConfirmation
        every { viewModel.showUniversalPasswordNotice } returns passwordNotice
        every { viewModel.universalPasswordSwitchResult } returns passwordResult
    }

    @After
    fun tearDown() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun optionsButtonsReachTheViewModel() {
        host = hostSheet()

        compose.onNodeWithText(string(R.string.common_title_wallet_option)).assertExists()
        compose.onNodeWithText(string(R.string.export_wallet)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.change_wallet_name)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithContentDescription(string(R.string.ux_close)).performClick()
        settle()

        verify { viewModel.onBackupWalletClick() }
        verify { viewModel.onChangeWalletNameClick() }
        verify { viewModel.onCloseClick() }
    }

    @Test
    fun deleteConfirmationForgetsTheWalletOnlyAfterConfirming() {
        host = hostSheet()

        deleteConfirmation.value = Event(7L)
        settle()

        assertTrue(fragment.childFragmentManager.findFragmentByTag(ErrorDialog.TAG) is ErrorDialog)
        compose.onNodeWithText(string(R.string.account_delete_confirmation_title)).assertExists()
        verify(exactly = 0) { viewModel.deleteWalletConfirmed() }

        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.account_delete_confirm)).performClick()
        settle()

        verify(exactly = 1) { viewModel.deleteWalletConfirmed() }
    }

    @Test
    fun oldAddressNoticeSwitchesShowsTheResultAndReportsItsClose() {
        host = hostSheet()
        val state = UniversalPasswordNoticeState(7L, listOf(Row(UniversalWalletSlot.TON, "OldTon", "NewTon")))

        passwordNotice.value = Event(state)
        ShadowLooper.idleMainLooper()
        val sheet = ShadowDialog.getLatestDialog() as UniversalPasswordNoticeSheet
        passwordNotice.value = Event(state)
        ShadowLooper.idleMainLooper()
        assertSame(sheet, ShadowDialog.getLatestDialog())

        sheet.findViewById<PrimaryButton>(R.id.universalPasswordSwitch)!!.performClick()
        verify { viewModel.onUniversalPasswordSwitch() }

        passwordResult.value = Event(UniversalPasswordNoticeState(7L, listOf(Row(UniversalWalletSlot.TON, "OldTon", "NewTon", outcome = Outcome.SWITCHED))))
        ShadowLooper.idleMainLooper()
        assertEquals(string(R.string.common_ok), sheet.findViewById<PrimaryButton>(R.id.universalPasswordSwitch)!!.text.toString())

        sheet.findViewById<Button>(R.id.universalPasswordLater)!!.performClick()
        ShadowLooper.idleMainLooper()

        assertFalse(sheet.isShowing)
        verify { viewModel.onUniversalPasswordNoticeClosed() }
    }

    @Test
    fun bundleCarriesTheWalletAndWhetherDetailsAreAllowed() {
        val bundle = OptionsWalletFragment.getBundle(walletId = 7L, allowDetails = false)

        assertEquals(7L, bundle.getLong(OptionsWalletFragment.KEY_WALLET_ID))
        assertFalse(bundle.getBoolean(OptionsWalletFragment.KEY_ALLOW_WALLET_DETAILS))
        assertTrue(OptionsWalletFragment.getBundle(8L).getBoolean(OptionsWalletFragment.KEY_ALLOW_WALLET_DETAILS))
    }

    private fun hostSheet(): ActivityController<StubHiltActivity> {
        fragment = OptionsWalletFragment()
        fragment.arguments = OptionsWalletFragment.getBundle(7L)
        val controller = HiltFragmentHost.host(fragment, mapOf(OptionsWalletViewModel::class.java to viewModel))
        settle()
        return controller
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }
}

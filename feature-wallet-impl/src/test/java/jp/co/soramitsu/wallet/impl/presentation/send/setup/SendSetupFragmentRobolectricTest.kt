package jp.co.soramitsu.wallet.impl.presentation.send.setup

import android.graphics.Rect
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.compose.component.AddressInputWithScore
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WarningInfoState
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogContract
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogResult
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The send setup sheet renders its form, reports the keyboard and routes transfer warnings through its dialog. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h2000dp")
class SendSetupFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val edWarning = TransferValidationResult.ExistentialDepositWarning("0.01 DOT")
    private val warning = ValidationWarning("Below the deposit", "The account may be reaped", "Keep", "Cancel", "Send all")
    private val warningEvent = MutableLiveData<Event<Pair<TransferValidationResult, ValidationWarning>>>()
    private var host: ActivityController<StubHiltActivity>? = null
    private lateinit var viewModel: SendSetupViewModel

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
        viewModel = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(setupState())
            every { openScannerEvent } returns MutableLiveData()
            every { openValidationWarningEvent } returns warningEvent
            every { isSoftKeyboardOpenFlow } returns MutableStateFlow(false)
            every { onCrossChainClick } returns {}
        }
    }

    @After
    fun closeHost() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun sheetRendersTheFormExpandedAndForwardsTheNextClick() {
        val fragment = hostSendSetup()

        compose.onNodeWithText("Paste a Polkadot address").assertExists()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()

        verify { viewModel.onNextClick() }
        val behavior = (fragment.requireDialog() as BottomSheetDialog).behavior
        assertTrue(behavior.isHideable)
        assertTrue(
            "state ${behavior.state}",
            behavior.state == BottomSheetBehavior.STATE_EXPANDED || behavior.state == BottomSheetBehavior.STATE_SETTLING
        )
    }

    @Test
    fun globalLayoutReportsWhetherTheKeyboardCoversTheSheet() {
        val fragment = hostSendSetup()
        val view = fragment.requireView()
        val visible = Rect().also { view.getWindowVisibleDisplayFrame(it) }
        val keyboardOpen = view.rootView.height - (visible.bottom - visible.top) > 500

        view.viewTreeObserver.dispatchOnGlobalLayout()

        verify { viewModel.setSoftKeyboardOpen(keyboardOpen) }
    }

    @Test
    fun approvableWarningOpensTheDialogAndEachButtonReachesTheViewModel() {
        val fragment = hostSendSetup()

        showWarning(fragment, warning)
        answer(fragment, ErrorDialog.Action.SECOND_POSITIVE)
        verify(exactly = 1) { viewModel.warningConfirmedSecond(edWarning) }

        dismissDialog(fragment)
        showWarning(fragment, ValidationWarning("Still below", "Reaped", "Keep", "Cancel", null))
        answer(fragment, ErrorDialog.Action.NEGATIVE)
        verify(exactly = 1) { viewModel.warningCancelled(edWarning) }

        dismissDialog(fragment)
        showWarning(fragment, warning)
        answer(fragment, ErrorDialog.Action.POSITIVE)
        verify(exactly = 1) { viewModel.warningConfirmed(edWarning) }
    }

    @Test
    fun warningThatCannotBeApprovedOpensNoDialog() {
        val fragment = hostSendSetup()

        warningEvent.value = Event(TransferValidationResult.InsufficientBalance to warning)
        ShadowLooper.idleMainLooper()

        assertNull(fragment.childFragmentManager.findFragmentByTag(DIALOG_TAG))
        assertTrue(fragment.childFragmentManager.fragments.none { it is ErrorDialog })
    }

    @Test
    @Suppress("DEPRECATION")
    fun bundleCarriesThePrefilledTransfer() {
        val payload = AssetPayload("polkadot", "dot")

        val bundle = SendSetupFragment.getBundle(payload, "5Grwva", "currency", BigDecimal.ONE, true)
        val empty = SendSetupFragment.getBundle(null, null, null, null, false)

        assertEquals(payload, bundle.get(SendSetupFragment.KEY_PAYLOAD))
        assertEquals("5Grwva", bundle.getString(SendSetupFragment.KEY_INITIAL_ADDRESS))
        assertEquals("currency", bundle.getString(SendSetupFragment.KEY_TOKEN_ID))
        assertEquals(BigDecimal.ONE, bundle.get(SendSetupFragment.KEY_INITIAL_AMOUNT))
        assertTrue(bundle.getBoolean(SendSetupFragment.KEY_LOCK_AMOUNT))
        assertTrue(empty.containsKey(SendSetupFragment.KEY_PAYLOAD))
        assertNull(empty.get(SendSetupFragment.KEY_PAYLOAD))
        assertNull(empty.get(SendSetupFragment.KEY_INITIAL_AMOUNT))
        assertEquals(false, empty.getBoolean(SendSetupFragment.KEY_LOCK_AMOUNT, true))
    }

    private fun hostSendSetup(): SendSetupFragment {
        val fragment = SendSetupFragment()
        host = HiltFragmentHost.host(fragment, mapOf(SendSetupViewModel::class.java to viewModel))
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        return fragment
    }

    private fun showWarning(fragment: SendSetupFragment, shown: ValidationWarning) {
        warningEvent.value = Event(edWarning to shown)
        ShadowLooper.idleMainLooper()
        assertNotNull(fragment.childFragmentManager.findFragmentByTag(DIALOG_TAG) as? ErrorDialog)
    }

    private fun answer(fragment: SendSetupFragment, action: ErrorDialog.Action) {
        val dialog = fragment.childFragmentManager.findFragmentByTag(DIALOG_TAG) as ErrorDialog
        val payload = BundleCompat.getParcelable(dialog.requireArguments(), ErrorDialog.RESULT_PAYLOAD, TransferValidationDialogResult::class.java)
        assertEquals(edWarning, payload!!.validationResult)
        fragment.childFragmentManager.setFragmentResult(
            TransferValidationDialogContract.SEND_SETUP.requestKey,
            bundleOf(ErrorDialog.RESULT_ACTION to action.name, ErrorDialog.RESULT_PAYLOAD to payload)
        )
        ShadowLooper.idleMainLooper()
    }

    private fun dismissDialog(fragment: SendSetupFragment) {
        (fragment.childFragmentManager.findFragmentByTag(DIALOG_TAG) as ErrorDialog).dismissNow()
        ShadowLooper.idleMainLooper()
    }

    private fun setupState() = SendSetupViewState(
        toolbarState = ToolbarViewState("Send", R.drawable.ic_arrow_left_24),
        addressInputState = AddressInputWithScore.Empty("Send to", "Paste a Polkadot address"),
        amountInputState = AmountInputViewState(
            tokenName = "DOT",
            tokenImage = "",
            totalBalance = "Balance: 10 DOT",
            fiatAmount = "$60",
            tokenAmount = BigDecimal("1"),
            title = "Amount",
            isFocused = false,
            allowAssetChoose = true
        ),
        chainSelectorState = SelectorState("Network", "Polkadot", null),
        feeInfoState = FeeInfoViewState("Network fee", "0.01 DOT", "$0.06"),
        warningInfoState = WarningInfoState("Fee is high", emptyList()),
        buttonState = ButtonViewState("Continue", true),
        isSoftKeyboardOpen = false,
        isInputLocked = false,
        isHistoryAvailable = true,
        sendAllChecked = false,
        sendAllAllowed = true,
        commentState = TextInputViewState("", "Comment"),
        recipientHint = "Send to your own account",
        nativeReview = false,
        crossChainAvailable = true
    )

    private companion object {
        const val DIALOG_TAG = "${ErrorDialog.TAG}:transfer-validation:send_setup_validation_dialog_result"
    }
}

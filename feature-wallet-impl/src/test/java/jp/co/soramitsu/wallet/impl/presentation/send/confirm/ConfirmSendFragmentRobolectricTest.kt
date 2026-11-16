package jp.co.soramitsu.wallet.impl.presentation.send.confirm

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.ExternalActionsSheet
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogContract
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogResult
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/** The send confirmation sheet confirms, opens explorer actions and routes existential-deposit warnings. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h2000dp")
class ConfirmSendFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val edWarning = TransferValidationResult.UtilityExistentialDepositWarning("0.01 DOT")
    private val warning = ValidationWarning("Fee drops the balance", "The account may be reaped", "Send anyway", "Cancel", null)
    private val warningEvent = MutableLiveData<Event<Pair<TransferValidationResult, ValidationWarning>>>()
    private val browserEvent = MutableLiveData<Event<String>>()
    private val externalActionsEvent = MutableLiveData<Event<ExternalAccountActions.Payload>>()
    private var host: ActivityController<StubHiltActivity>? = null
    private lateinit var viewModel: ConfirmSendViewModel

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
        viewModel = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(confirmState())
            every { openValidationWarningEvent } returns warningEvent
            every { openBrowserEvent } returns browserEvent
            every { showExternalActionsEvent } returns externalActionsEvent
            every { isSoftKeyboardOpenFlow } returns MutableStateFlow(false)
        }
    }

    @After
    fun closeHost() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun sheetRendersTheReviewAndForwardsTheConfirmation() {
        hostConfirmSend()

        compose.onNodeWithText("Polkadot").assertExists()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitForIdle()

        verify { viewModel.onNextClick() }
    }

    @Test
    fun explorerEventsOpenTheActionsSheetAndTheBrowser() {
        hostConfirmSend()

        externalActionsEvent.value = Event(
            ExternalAccountActions.Payload("5FHneW", mapOf(Chain.Explorer.Type.SUBSCAN to "https://polkadot.subscan.io/account/5FHneW"))
        )
        ShadowLooper.idleMainLooper()
        val sheet = ShadowDialog.getLatestDialog()
        assertTrue(sheet is ExternalActionsSheet)
        assertTrue(sheet.isShowing)
        sheet.dismiss()

        browserEvent.value = Event("https://polkadot.subscan.io/account/5FHneW")
        ShadowLooper.idleMainLooper()
        val opened = shadowOf(host!!.get()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("https://polkadot.subscan.io/account/5FHneW", opened.dataString)
    }

    @Test
    fun confirmedWarningReachesTheViewModel() {
        val fragment = hostConfirmSend()

        showWarning(fragment)
        answer(fragment, ErrorDialog.Action.POSITIVE)

        verify(exactly = 1) { viewModel.warningConfirmed(edWarning) }
    }

    @Test
    fun cancelledWarningIsDroppedWithoutConfirming() {
        val fragment = hostConfirmSend()

        showWarning(fragment)
        answer(fragment, ErrorDialog.Action.NEGATIVE)

        verify(exactly = 0) { viewModel.warningConfirmed(any()) }
    }

    @Test
    fun warningThatCannotBeApprovedOpensNoDialog() {
        val fragment = hostConfirmSend()

        warningEvent.value = Event(TransferValidationResult.ExistentialDepositError("0.01 DOT") to warning)
        ShadowLooper.idleMainLooper()

        assertNull(fragment.childFragmentManager.findFragmentByTag(DIALOG_TAG))
    }

    @Test
    @Suppress("DEPRECATION")
    fun bundleCarriesTheDraftAndItsOptions() {
        val draft = TransferDraft(BigDecimal.ONE, BigDecimal("0.01"), AssetPayload("polkadot", "dot"), "5FHneW", null, null)
        val overrides = mapOf<String, Any?>(ConfirmSendFragment.KEY_OVERRIDE_TO_VALUE to "Alice")

        val bundle = ConfirmSendFragment.getBundle(draft, PhishingType.SCAM, overrides, "thanks")
        val skipping = ConfirmSendFragment.getBundle(draft, null, emptyMap(), null, skipEdValidation = true)

        assertSame(draft, bundle.get(ConfirmSendFragment.KEY_DRAFT))
        assertEquals(PhishingType.SCAM, bundle.get(ConfirmSendFragment.KEY_PHISHING_TYPE))
        assertEquals(overrides, bundle.get(ConfirmSendFragment.KEY_OVERRIDES))
        assertEquals("thanks", bundle.getString(ConfirmSendFragment.KEY_TRANSFER_COMMENT))
        assertFalse(bundle.getBoolean(ConfirmSendFragment.KEY_SKIP_ED_VALIDATION, true))
        assertNull(skipping.get(ConfirmSendFragment.KEY_PHISHING_TYPE))
        assertNull(skipping.getString(ConfirmSendFragment.KEY_TRANSFER_COMMENT))
        assertTrue(skipping.getBoolean(ConfirmSendFragment.KEY_SKIP_ED_VALIDATION))
    }

    private fun hostConfirmSend(): ConfirmSendFragment {
        val fragment = ConfirmSendFragment()
        host = HiltFragmentHost.host(fragment, mapOf(ConfirmSendViewModel::class.java to viewModel))
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        return fragment
    }

    private fun showWarning(fragment: ConfirmSendFragment) {
        warningEvent.value = Event(edWarning to warning)
        ShadowLooper.idleMainLooper()
        assertNotNull(fragment.childFragmentManager.findFragmentByTag(DIALOG_TAG) as? ErrorDialog)
    }

    private fun answer(fragment: ConfirmSendFragment, action: ErrorDialog.Action) {
        val dialog = fragment.childFragmentManager.findFragmentByTag(DIALOG_TAG) as ErrorDialog
        val payload = BundleCompat.getParcelable(dialog.requireArguments(), ErrorDialog.RESULT_PAYLOAD, TransferValidationDialogResult::class.java)
        assertEquals(edWarning, payload!!.validationResult)
        fragment.childFragmentManager.setFragmentResult(
            TransferValidationDialogContract.CONFIRM_SEND.requestKey,
            bundleOf(ErrorDialog.RESULT_ACTION to action.name, ErrorDialog.RESULT_PAYLOAD to payload)
        )
        ShadowLooper.idleMainLooper()
    }

    private fun confirmState() = ConfirmSendViewState(
        chainIconUrl = null,
        networkInfoItem = TitleValueViewState("Network", "Polkadot"),
        fromInfoItem = TitleValueViewState("From", "Main", additionalValue = "5Grwva"),
        toInfoItem = TitleValueViewState("To", "5FHneW"),
        amountInfoItem = TitleValueViewState("Amount", "1 DOT", additionalValue = "$6"),
        feeInfoItem = TitleValueViewState(
            "Existential deposit",
            "0.01 DOT",
            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, ConfirmSendViewState.CODE_WARNING_CLICK)
        ),
        buttonState = ButtonViewState("Confirm", true),
        nativeReview = true,
        // A bundled icon keeps the test off the network.
        iconOverrideResId = R.drawable.ic_fearless_logo
    )

    private companion object {
        const val DIALOG_TAG = "${ErrorDialog.TAG}:transfer-validation:confirm_send_validation_dialog_result"
    }
}

package jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm

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
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogContract
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogResult
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.CrossChainTransferDraft
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The cross-chain confirmation page confirms, opens the explorer and routes approvable warnings. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h2000dp")
class CrossChainConfirmFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val edWarning = TransferValidationResult.ExistentialDepositWarning("0.01 DOT")
    private val warning = ValidationWarning("Below the deposit", "The account may be reaped", "Send anyway", "Cancel", null)
    private val warningEvent = MutableLiveData<Event<Pair<TransferValidationResult, ValidationWarning>>>()
    private val browserEvent = MutableLiveData<Event<String>>()
    private var host: ActivityController<StubHiltActivity>? = null
    private lateinit var viewModel: CrossChainConfirmViewModel

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
        viewModel = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(confirmState())
            every { openValidationWarningEvent } returns warningEvent
            every { openBrowserEvent } returns browserEvent
            every { showExternalActionsEvent } returns MutableLiveData<Event<ExternalAccountActions.Payload>>()
            every { isSoftKeyboardOpenFlow } returns MutableStateFlow(false)
        }
    }

    @After
    fun closeHost() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun pageRendersTheTransferAndForwardsTheConfirmation() {
        hostCrossChainConfirm()

        compose.onNodeWithText("2 DOT").assertExists()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitForIdle()

        verify { viewModel.onNextClick() }
    }

    @Test
    fun browserEventOpensTheExplorer() {
        hostCrossChainConfirm()

        browserEvent.value = Event("https://polkadot.subscan.io/extrinsic/0xabc")
        ShadowLooper.idleMainLooper()

        val opened = shadowOf(host!!.get()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("https://polkadot.subscan.io/extrinsic/0xabc", opened.dataString)
    }

    @Test
    fun confirmedWarningReachesTheViewModel() {
        val fragment = hostCrossChainConfirm()

        showWarning(fragment)
        answer(fragment, ErrorDialog.Action.POSITIVE)

        verify(exactly = 1) { viewModel.warningConfirmed(edWarning) }
    }

    @Test
    fun cancelledWarningIsDroppedWithoutConfirming() {
        val fragment = hostCrossChainConfirm()

        showWarning(fragment)
        answer(fragment, ErrorDialog.Action.NEGATIVE)

        verify(exactly = 0) { viewModel.warningConfirmed(any()) }
    }

    @Test
    fun warningThatCannotBeApprovedOpensNoDialog() {
        val fragment = hostCrossChainConfirm()

        warningEvent.value = Event(TransferValidationResult.CrossChainDestinationUnavailable to warning)
        ShadowLooper.idleMainLooper()

        assertNull(fragment.childFragmentManager.findFragmentByTag(DIALOG_TAG))
    }

    @Test
    @Suppress("DEPRECATION")
    fun bundleCarriesTheDraftAndPhishingType() {
        val draft = CrossChainTransferDraft(
            amount = BigDecimal("2"),
            originChainId = "polkadot",
            destinationChainId = "asset-hub",
            originFee = BigDecimal("0.01"),
            destinationFee = BigDecimal("0.02"),
            chainAssetId = "dot",
            recipientAddress = "5FHneW",
            tip = null,
            transferableTokenSymbol = "DOT",
            metaAccountId = 1L
        )

        val bundle = CrossChainConfirmFragment.getBundle(draft, PhishingType.EXCHANGE)
        val unflagged = CrossChainConfirmFragment.getBundle(draft, null)

        assertSame(draft, bundle.get(CrossChainConfirmFragment.KEY_DRAFT))
        assertEquals(PhishingType.EXCHANGE, bundle.get(CrossChainConfirmFragment.KEY_PHISHING_TYPE))
        assertSame(draft, unflagged.get(CrossChainConfirmFragment.KEY_DRAFT))
        assertNull(unflagged.get(CrossChainConfirmFragment.KEY_PHISHING_TYPE))
    }

    private fun hostCrossChainConfirm(): CrossChainConfirmFragment {
        val fragment = CrossChainConfirmFragment()
        host = HiltFragmentHost.host(fragment, mapOf(CrossChainConfirmViewModel::class.java to viewModel))
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        return fragment
    }

    private fun showWarning(fragment: CrossChainConfirmFragment) {
        warningEvent.value = Event(edWarning to warning)
        ShadowLooper.idleMainLooper()
        assertNotNull(fragment.childFragmentManager.findFragmentByTag(DIALOG_TAG) as? ErrorDialog)
    }

    private fun answer(fragment: CrossChainConfirmFragment, action: ErrorDialog.Action) {
        val dialog = fragment.childFragmentManager.findFragmentByTag(DIALOG_TAG) as ErrorDialog
        val payload = BundleCompat.getParcelable(dialog.requireArguments(), ErrorDialog.RESULT_PAYLOAD, TransferValidationDialogResult::class.java)
        assertEquals(edWarning, payload!!.validationResult)
        fragment.childFragmentManager.setFragmentResult(
            TransferValidationDialogContract.CROSS_CHAIN_CONFIRM.requestKey,
            bundleOf(ErrorDialog.RESULT_ACTION to action.name, ErrorDialog.RESULT_PAYLOAD to payload)
        )
        ShadowLooper.idleMainLooper()
    }

    private fun confirmState() = CrossChainConfirmViewState(
        originChainIcon = null,
        destinationChainIcon = null,
        fromInfoItem = TitleValueViewState("From", "Main"),
        toInfoItem = TitleValueViewState("To", "5FHneW"),
        originNetworkItem = TitleValueViewState("Origin", "Polkadot"),
        destinationNetworkItem = TitleValueViewState("Destination", "Asset Hub"),
        amountInfoItem = TitleValueViewState("Amount", "2 DOT"),
        originFeeInfoItem = TitleValueViewState(
            "Origin fee",
            "0.01 DOT",
            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, CrossChainConfirmViewState.CODE_WARNING_CLICK)
        ),
        buttonState = ButtonViewState("Confirm", true)
    )

    private companion object {
        const val DIALOG_TAG = "${ErrorDialog.TAG}:transfer-validation:cross_chain_confirm_validation_dialog_result"
    }
}

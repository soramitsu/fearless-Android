package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
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
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
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
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The cross-chain setup page renders its form, reports the keyboard and confirms approvable warnings. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h3000dp")
class CrossChainSetupFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val bridgeWarning = TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning("SORA")
    private val warning = ValidationWarning("Amount below the fee", "The bridge keeps the amount", "Send anyway", "Cancel", null)
    private val warningEvent = MutableLiveData<Event<Pair<TransferValidationResult, ValidationWarning>>>()
    private var host: ActivityController<StubHiltActivity>? = null
    private lateinit var viewModel: CrossChainSetupViewModel

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
        viewModel = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(setupState())
            every { openScannerEvent } returns MutableSharedFlow()
            every { openValidationWarningEvent } returns warningEvent
            every { isSoftKeyboardOpenFlow } returns MutableStateFlow(false)
        }
    }

    @After
    fun closeHost() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun pageRendersTheRouteFormAndForwardsTheNextClick() {
        hostCrossChainSetup()

        compose.onNodeWithText("Checking route…").assertExists()
        compose.onNodeWithText("Origin network").assertExists()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()

        verify { viewModel.onNextClick() }
    }

    @Test
    fun globalLayoutReportsWhetherTheKeyboardCoversThePage() {
        val fragment = hostCrossChainSetup()
        val view = fragment.requireView()
        val visible = Rect().also { view.getWindowVisibleDisplayFrame(it) }
        val keyboardOpen = view.rootView.height - (visible.bottom - visible.top) > 500

        view.viewTreeObserver.dispatchOnGlobalLayout()

        verify { viewModel.setSoftKeyboardOpen(keyboardOpen) }
    }

    @Test
    fun confirmedBridgeWarningReachesTheViewModel() {
        val fragment = hostCrossChainSetup()

        showWarning(fragment)
        answer(fragment, ErrorDialog.Action.POSITIVE)

        verify(exactly = 1) { viewModel.warningConfirmed(bridgeWarning) }
    }

    @Test
    fun cancelledBridgeWarningIsDroppedWithoutConfirming() {
        val fragment = hostCrossChainSetup()

        showWarning(fragment)
        answer(fragment, ErrorDialog.Action.NEGATIVE)

        verify(exactly = 0) { viewModel.warningConfirmed(any()) }
    }

    @Test
    fun warningThatCannotBeApprovedOpensNoDialog() {
        val fragment = hostCrossChainSetup()

        warningEvent.value = Event(TransferValidationResult.InsufficientBalance to warning)
        ShadowLooper.idleMainLooper()

        assertNull(fragment.childFragmentManager.findFragmentByTag(DIALOG_TAG))
    }

    @Test
    @Suppress("DEPRECATION")
    fun bundleCarriesTheAssetPayload() {
        val payload = AssetPayload("polkadot", "dot")

        assertEquals(payload, CrossChainSetupFragment.getBundle(payload).get(CrossChainSetupFragment.KEY_PAYLOAD))
        val empty = CrossChainSetupFragment.getBundle(null)
        assertTrue(empty.containsKey(CrossChainSetupFragment.KEY_PAYLOAD))
        assertNull(empty.get(CrossChainSetupFragment.KEY_PAYLOAD))
    }

    private fun hostCrossChainSetup(): CrossChainSetupFragment {
        val fragment = CrossChainSetupFragment()
        host = HiltFragmentHost.host(fragment, mapOf(CrossChainSetupViewModel::class.java to viewModel))
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        return fragment
    }

    private fun showWarning(fragment: CrossChainSetupFragment) {
        warningEvent.value = Event(bridgeWarning to warning)
        ShadowLooper.idleMainLooper()
        assertNotNull(fragment.childFragmentManager.findFragmentByTag(DIALOG_TAG) as? ErrorDialog)
    }

    private fun answer(fragment: CrossChainSetupFragment, action: ErrorDialog.Action) {
        val dialog = fragment.childFragmentManager.findFragmentByTag(DIALOG_TAG) as ErrorDialog
        val payload = BundleCompat.getParcelable(dialog.requireArguments(), ErrorDialog.RESULT_PAYLOAD, TransferValidationDialogResult::class.java)
        assertEquals(bridgeWarning, payload!!.validationResult)
        fragment.childFragmentManager.setFragmentResult(
            TransferValidationDialogContract.CROSS_CHAIN_SETUP.requestKey,
            bundleOf(ErrorDialog.RESULT_ACTION to action.name, ErrorDialog.RESULT_PAYLOAD to payload)
        )
        ShadowLooper.idleMainLooper()
    }

    private fun setupState() = CrossChainSetupViewState(
        toolbarState = ToolbarViewState("Cross chain", R.drawable.ic_arrow_left_24),
        addressInputState = AddressInputState("Send to", "5Grwva", ""),
        amountInputState = AmountInputViewState(
            tokenName = "DOT",
            tokenImage = "",
            totalBalance = "Balance: 10 DOT",
            fiatAmount = "$60",
            tokenAmount = BigDecimal("1.5"),
            title = "Amount",
            isFocused = false,
            allowAssetChoose = true
        ),
        originChainSelectorState = SelectorState("Origin network", "Polkadot", null),
        destinationChainSelectorState = SelectorState("Destination network", "Asset Hub", null),
        originFeeInfoState = FeeInfoViewState("Origin fee", "0.01 DOT", "$0.06"),
        destinationFeeInfoState = FeeInfoViewState("Destination fee", "0.02 DOT", "$0.12"),
        warningInfoState = null,
        buttonState = ButtonViewState("Continue", true),
        walletIcon = BitmapDrawable(RuntimeEnvironment.getApplication().resources, Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)),
        isSoftKeyboardOpen = false,
        routeCapabilityMessage = "Checking route…",
        routeCapability = null,
        routeInventory = emptyList()
    )

    private companion object {
        const val DIALOG_TAG = "${ErrorDialog.TAG}:transfer-validation:cross_chain_setup_validation_dialog_result"
    }
}

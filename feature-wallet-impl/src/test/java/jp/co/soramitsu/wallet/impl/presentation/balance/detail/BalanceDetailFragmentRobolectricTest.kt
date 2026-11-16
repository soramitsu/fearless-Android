package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import android.app.Dialog
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import jp.co.soramitsu.account.api.presentation.accountSource.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.ExternalActionsSheet
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.account.api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.common.compose.component.ActionBarViewState
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AddressDisplayState
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChainSelectorViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.MainToolbarViewState
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarHomeIconState
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.data.network.send.PendingWalletSend
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendRecoverySnapshot
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.BuyMixin
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.wallet.impl.presentation.send.NativeSendRecoveryUiState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransactionDetailsState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransferDetailsState
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** The asset detail screen wires its toolbar, sheets, dialogs and share chooser to its view model. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h2400dp")
class BalanceDetailFragmentRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private val accountOptions = MutableLiveData<Event<AccountOptionsPayload>>()
    private val exportChooser = MutableLiveData<Event<ExportSourceChooserPayload>>()
    private val shareUrl = MutableLiveData<Event<String>>()
    private val externalSelector = MutableStateFlow<ExternalAccountActions.Payload?>(null)
    private val toolbar = MutableStateFlow<LoadingState<MainToolbarViewState>>(LoadingState.Loaded(toolbarViewState()))
    private val recovery = MutableStateFlow<NativeSendRecoveryUiState?>(null)
    private val details = MutableStateFlow<TransactionDetailsState?>(null)
    private val viewModel = mockk<BalanceDetailViewModel>(relaxed = true)
    private var host: ActivityController<StubHiltActivity>? = null

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
        every { viewModel.state } returns MutableStateFlow(detailsState())
        every { viewModel.toolbarState } returns toolbar
        every { viewModel.nativeRecoveryUi } returns recovery
        every { viewModel.transactionDetailsBottomSheet } returns details
        every { viewModel.externalActionsSelector } returns externalSelector
        every { viewModel.showAccountOptions } returns accountOptions
        every { viewModel.showExportSourceChooser } returns exportChooser
        every { viewModel.shareUrlEvent } returns shareUrl
        every { viewModel.showProviderChooserEvent } returns MutableLiveData<Event<BuyMixin.ProviderChooserPayload>>()
        every { viewModel.integrateWithBuyProviderEvent } returns MutableLiveData<Event<BuyMixin.IntegrationPayload>>()
        every { viewModel.showExternalActionsEvent } returns MutableLiveData<Event<ExternalAccountActions.Payload>>()
        every { viewModel.openBrowserEvent } returns MutableLiveData<Event<String>>()
    }

    @After
    fun tearDown() {
        HiltFragmentHost.close(host)
        unmockkObject(FwRestyle)
    }

    @Test
    fun hostingSyncsAndShowsTheToolbarAndBalance() {
        host = hostDetail()

        compose.onNodeWithText(WALLET_NAME).assertExists()
        compose.onNodeWithText("14,400.3").assertExists()
        verify { viewModel.sync() }
        // The hidden details sheet reports itself closed once the screen starts.
        verify { viewModel.onDetailsClose() }
    }

    @Test
    fun toolbarAndActionBarReachTheViewModel() {
        host = hostDetail()

        compose.onNodeWithContentDescription(string(R.string.ux_back)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithContentDescription(string(R.string.ux_more)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.common_action_send)).performClick()
        settle()

        verify { viewModel.backClicked() }
        verify { viewModel.accountOptionsClicked() }
        verify { viewModel.actionItemClicked(ActionItemType.SEND, "chain", "asset") }
    }

    @Test
    fun loadingToolbarShowsTheShimmerWithoutTheWalletName() {
        toolbar.value = LoadingState.Loading()
        host = hostDetail()

        assertTrue(compose.onAllNodesWithText(WALLET_NAME).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription(string(R.string.ux_back)).assertExists()
    }

    @Test
    fun substrateAccountOptionsWithClaimReachEveryAction() {
        host = hostDetail()

        repeat(4) { index ->
            val rows = showAccountOptions(AccountOptionsPayload("5Address", supportClaim = true, isEthereum = false))
            assertTrue(ShadowDialog.getLatestDialog() is BalanceDetailOptionsBottomSheet)
            assertEquals(4, rows.size)
            rows[index].performClick()
            ShadowLooper.idleMainLooper()
        }

        verify { viewModel.exportClicked() }
        verify { viewModel.switchNode() }
        verify { viewModel.copyAddress("5Address", any()) }
        verify { viewModel.claimRewardClicked() }
    }

    @Test
    fun substrateAccountOptionsWithoutClaimOfferThreeActions() {
        host = hostDetail()

        val rows = showAccountOptions(AccountOptionsPayload("5Address", supportClaim = false, isEthereum = false))

        assertEquals(3, rows.size)
        verify(exactly = 0) { viewModel.claimRewardClicked() }
    }

    @Test
    fun ethereumAccountOptionsOfferExportAndCopy() {
        host = hostDetail()

        val rows = showAccountOptions(AccountOptionsPayload("0xabc", supportClaim = true, isEthereum = true))
        assertTrue(ShadowDialog.getLatestDialog() is BalanceDetailEthereumOptionsBottomSheet)
        assertEquals(2, rows.size)
        rows[1].performClick()
        ShadowLooper.idleMainLooper()

        verify { viewModel.copyAddress("0xabc", any()) }
        verify(exactly = 0) { viewModel.exportClicked() }
    }

    @Test
    fun exportChooserSendsTheChosenSourceWithItsChain() {
        host = hostDetail()

        exportChooser.value = Event(ExportSourceChooserPayload("chain", setOf(ExportSource.Mnemonic, ExportSource.Seed)))
        ShadowLooper.idleMainLooper()
        val dialog = ShadowDialog.getLatestDialog()

        assertTrue(dialog is SourceTypeChooserBottomSheetDialog<*>)
        assertEquals(
            string(R.string.select_save_type),
            dialog.findViewById<TextView>(jp.co.soramitsu.common.R.id.dynamicListSheetTitle)!!.text.toString()
        )
        @Suppress("UNCHECKED_CAST")
        val chooser = dialog as DynamicListBottomSheet<ExportSource>
        chooser.itemClicked(ExportSource.Seed)
        ShadowLooper.idleMainLooper()

        verify { viewModel.exportTypeSelected(ExportSource.Seed, "chain") }
        assertFalse(dialog.isShowing)
    }

    @Test
    fun selectedAddressOpensActionsThatCopyAndBrowse() {
        host = hostDetail()

        externalSelector.value = ExternalAccountActions.Payload("0xhash", mapOf(Chain.Explorer.Type.SUBSCAN to EXPLORER_URL))
        ShadowLooper.idleMainLooper()
        val sheet = ShadowDialog.getLatestDialog() as ExternalActionsSheet

        assertEquals("0xhash", sheet.findViewById<TextView>(R.id.fixedListSheetTitle)!!.text.toString())
        sheet.onCopy("0xhash")
        sheet.onViewExternal(EXPLORER_URL)

        verify { viewModel.copyStringClicked("0xhash") }
        verify { viewModel.openUrl(EXPLORER_URL) }
    }

    @Test
    fun sharedUrlOpensTheSystemChooserWithPlainText() {
        host = hostDetail()

        shareUrl.value = Event(EXPLORER_URL)
        ShadowLooper.idleMainLooper()
        val chooser = shadowOf(host!!.get()).nextStartedActivity

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val shared = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, shared.action)
        assertEquals(EXPLORER_URL, shared.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("text/plain", shared.type)
        assertEquals(string(R.string.common_share), chooser.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
    }

    @Test
    fun transferDetailsAreComposedIntoTheSheet() {
        host = hostDetail()

        details.value = transferDetails()
        settle()

        compose.onNodeWithText("0xdetailhash").assertExists()
        compose.onNodeWithText("5Sender").assertExists()
    }

    @Test
    fun pendingNativePaymentDialogActionsReachTheViewModel() {
        recovery.value = NativeSendRecoveryUiState(
            NativeSendRecoverySnapshot(
                walletId = 1, chainId = "chain", sender = "me", selectionEpoch = 1, transactionId = "tx-1",
                state = PendingWalletSend.State.Pending
            )
        )
        host = hostDetail()

        compose.onNodeWithText(string(R.string.send_check_status)).performClick()
        compose.onNodeWithText(string(R.string.send_retry_same_payment)).performClick()
        compose.onNodeWithText(string(R.string.send_view_explorer)).performClick()
        compose.onNodeWithText(string(R.string.common_close)).performClick()
        settle()

        verify { viewModel.refreshNativePayment(false) }
        verify { viewModel.refreshNativePayment(true) }
        verify { viewModel.openNativePaymentExplorer() }
        verify { viewModel.closeNativePayment() }
    }

    @Test
    fun legacyPaletteDrawsThePageImageBehindTheDetails() {
        mockkObject(FwRestyle)
        every { FwRestyle.paletteEnabled } returns false
        every { FwRestyle.isEnabled(any()) } returns false

        host = hostDetail()

        compose.onNodeWithText(WALLET_NAME).assertExists()
        compose.onNodeWithText("14,400.3").assertExists()
    }

    @Test
    fun bundleCarriesTheExactAsset() {
        val payload = AssetPayload("polkadot", "dot")

        val bundle = BalanceDetailFragment.getBundle(payload)

        @Suppress("DEPRECATION")
        val stored = bundle.get(KEY_ASSET_PAYLOAD)
        assertEquals(payload, stored)
    }

    private fun hostDetail(): ActivityController<StubHiltActivity> {
        val fragment = BalanceDetailFragment()
        fragment.arguments = BalanceDetailFragment.getBundle(AssetPayload("chain", "asset"))
        val controller = HiltFragmentHost.host(fragment, mapOf(BalanceDetailViewModel::class.java to viewModel))
        settle()
        return controller
    }

    private fun showAccountOptions(payload: AccountOptionsPayload): List<View> {
        accountOptions.value = Event(payload)
        ShadowLooper.idleMainLooper()
        return sheetRows(ShadowDialog.getLatestDialog())
    }

    private fun sheetRows(dialog: Dialog): List<View> {
        val container = dialog.findViewById<LinearLayout>(R.id.fixedListSheetItemContainer)!!
        return (0 until container.childCount).map { container.getChildAt(it) }.filter { it.isClickable }
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun toolbarViewState() = MainToolbarViewState(
        title = WALLET_NAME,
        homeIconState = ToolbarHomeIconState.Navigation(navigationIcon = R.drawable.ic_arrow_back_24dp),
        selectorViewState = ChainSelectorViewState(selectedChainName = "Polkadot Relay", selectedChainId = "chain")
    )

    private fun detailsState() = BalanceDetailsState(
        actionBarViewState = LoadingState.Loaded(
            ActionBarViewState("chain", "asset", listOf(ActionItemType.SEND, ActionItemType.RECEIVE))
        ),
        balance = LoadingState.Loaded(
            AssetBalanceViewState("14,400.3", "0x3214123", isInfoEnabled = false, changeViewState = ChangeBalanceViewState("+5%", "$1"))
        ),
        transferableViewState = TitleValueViewState(title = "Transferable", value = "10 DOT"),
        lockedViewState = TitleValueViewState(title = "Locked", value = "4 DOT"),
        legacyCrowdloanViewState = null,
        transactionHistory = TransactionHistoryUi.State.Empty(message = "See the explorer", explorerOnly = true),
        filtersEnabled = true
    )

    private fun transferDetails() = TransferDetailsState(
        hash = TextInputViewState(text = "0xdetailhash", hint = "Hash", endIcon = R.drawable.ic_more_vertical, isActive = false),
        firstAddress = AddressDisplayState("From", "5Sender", jp.co.soramitsu.common.R.drawable.ic_address_placeholder, R.drawable.ic_more_vertical),
        secondAddress = AddressDisplayState("To", "5Recipient", jp.co.soramitsu.common.R.drawable.ic_address_placeholder, R.drawable.ic_more_vertical),
        status = OperationStatusAppearance.COMPLETED,
        items = listOf(TitleValueViewState(title = "Amount", value = "1.5 DOT"))
    )

    private companion object {
        const val WALLET_NAME = "Main wallet"
        const val EXPLORER_URL = "https://polkadot.subscan.io/extrinsic/0xhash"
    }
}

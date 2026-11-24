package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.common.compose.component.ActionBarViewState
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.model.DayHeader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h2400dp")
class BalanceDetailsScreenRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : BalanceDetailsScreenInterface {
        override fun onAddressClick() { calls += "address" }
        override fun actionItemClicked(actionType: ActionItemType, chainId: String, chainAssetId: String) {
            calls += "action:$actionType:$chainId:$chainAssetId"
        }
        override fun filterClicked() { calls += "filter" }
        override fun transactionClicked(transactionModel: OperationModel) { calls += "tx:${transactionModel.id}" }
        override fun sync() { calls += "sync" }
        override fun transactionsScrolled(index: Int) { calls += "scrolled" }
        override fun tableItemClicked(itemId: Int) { calls += "table:$itemId" }
        override fun onRefresh() { calls += "refresh" }
        override fun onNativePaymentClick() { calls += "nativePayment" }
        override fun onNativeHistoryClick() { calls += "nativeHistory" }
    }

    @Before
    fun setUp() {
        ComposeFixtures.passDoubleTapGuard()
    }

    @After
    fun tearDown() {
        unmockkObject(FwRestyle)
    }

    @Test
    fun loadedDetailsShowBalancesActionsAndHistory() {
        render(
            state(
                history = TransactionHistoryUi.State.Data(
                    listOf(
                        DayHeader(19_000),
                        operation("t1", "Alice", OperationModel.Type.Transfer, OperationStatusAppearance.PENDING),
                        operation("s1", "Swap", OperationModel.Type.Swap, OperationStatusAppearance.FAILED),
                        operation("r1", "Reward", OperationModel.Type.Reward, OperationStatusAppearance.COMPLETED)
                    )
                ),
                nativePayment = true,
                nativeHistory = true
            )
        )

        compose.onNodeWithText("14,400.3").assertIsDisplayed()
        compose.onNodeWithText("Transferable").assertIsDisplayed()
        compose.onNodeWithText("Crowdloans").performClick()
        compose.onNodeWithText(string(R.string.common_all_transactions)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.send_recent_payment)).performClick()
        compose.onNodeWithText(string(R.string.send_history_explorer)).performClick()
        compose.onNodeWithText(string(R.string.common_action_send)).performClick()
        compose.onNodeWithText("Alice").performClick()
        compose.onNodeWithText("Reward").performClick()
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.containsAll(listOf("table:2", "nativePayment", "nativeHistory", "action:SEND:chain:asset", "tx:t1", "tx:r1")))
        assertEquals(2, compose.onAllNodes(hasText("Swap")).fetchSemanticsNodes().size)
    }

    @Test
    fun loadingStatesShowShimmersAndEmptyProgress() {
        render(
            state(history = TransactionHistoryUi.State.EmptyProgress).copy(
                actionBarViewState = LoadingState.Loading(),
                balance = LoadingState.Loading()
            )
        )

        compose.onNodeWithText(string(R.string.common_all_transactions)).assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("14,400.3")).fetchSemanticsNodes().size)
    }

    @Test
    fun explorerOnlyEmptyHistoryShowsTheMessage() {
        render(state(history = TransactionHistoryUi.State.Empty(message = "See the explorer", explorerOnly = true)))

        compose.onNodeWithText("See the explorer").assertIsDisplayed()
    }

    @Test
    fun emptyHistoryWithoutExplorerSaysThereAreNoTransfers() {
        render(state(history = TransactionHistoryUi.State.Empty(message = "ignored", explorerOnly = false)))

        compose.onNodeWithText(string(R.string.transfers_empty)).assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("ignored")).fetchSemanticsNodes().size)
    }

    @Test
    fun legacyPaletteRendersPlainEmptyHistoryAndItems() {
        mockkObject(FwRestyle)
        every { FwRestyle.isEnabled(any()) } returns false

        render(state(history = TransactionHistoryUi.State.Empty(message = "See the explorer", explorerOnly = true)))
        compose.onNodeWithText("See the explorer").assertIsDisplayed()
    }

    @Test
    fun legacyPaletteTransactionsHaveNoStatusRing() {
        mockkObject(FwRestyle)
        every { FwRestyle.isEnabled(any()) } returns false

        render(
            state(
                history = TransactionHistoryUi.State.Data(
                    listOf(operation("t2", "Bob", OperationModel.Type.Transfer, OperationStatusAppearance.PENDING, iconUrl = "https://x/y.svg"))
                )
            )
        )
        compose.onNodeWithText("Bob").performClick()
        compose.waitForIdle()

        assertTrue(calls.contains("tx:t2"))
    }

    private fun render(state: BalanceDetailsState) {
        compose.setContent {
            FearlessAppTheme {
                BalanceDetailsScreenWithRefreshBox(state = state, callback = callback)
            }
        }
        compose.waitForIdle()
    }

    private fun operation(
        id: String,
        header: String,
        type: OperationModel.Type,
        status: OperationStatusAppearance,
        iconUrl: String? = null
    ) = OperationModel(
        id = id,
        time = 1_700_000_000_000,
        amount = "-1.5 DOT",
        amountColor = Color.White,
        header = header,
        statusAppearance = status,
        operationIcon = null,
        subHeader = "Transfer",
        type = type,
        assetIconUrl = iconUrl
    )

    private fun state(
        history: TransactionHistoryUi.State,
        nativePayment: Boolean = false,
        nativeHistory: Boolean = false
    ) = BalanceDetailsState(
        actionBarViewState = LoadingState.Loaded(
            ActionBarViewState("chain", "asset", listOf(ActionItemType.SEND, ActionItemType.RECEIVE, ActionItemType.SWAP))
        ),
        balance = LoadingState.Loaded(
            AssetBalanceViewState("14,400.3", "0x3214123", isInfoEnabled = false, changeViewState = ChangeBalanceViewState("+5%", "$1"))
        ),
        transferableViewState = TitleValueViewState(title = "Transferable", value = "10 DOT"),
        lockedViewState = TitleValueViewState(
            title = "Locked",
            value = "4 DOT",
            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, 1)
        ),
        legacyCrowdloanViewState = TitleValueViewState(
            title = "Crowdloans",
            value = "1 DOT",
            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, 2)
        ),
        transactionHistory = history,
        filtersEnabled = true,
        nativePaymentAvailable = nativePayment,
        nativeHistoryExplorer = nativeHistory
    )
}

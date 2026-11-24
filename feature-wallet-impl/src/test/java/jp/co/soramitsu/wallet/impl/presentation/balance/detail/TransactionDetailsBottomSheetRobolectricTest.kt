package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.compose.material.ModalBottomSheetState
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.component.AddressDisplayState
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.SwapDetailState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransactionDetailsState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransferDetailsState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap.SwapStatusAppearance
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The asset detail's transaction sheet forwards each transfer and swap action to the detail view model. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h1600dp")
class TransactionDetailsBottomSheetRobolectricTest {

    @get:Rule
    val compose = createWalletComposeRule()

    private val viewModel = mockk<BalanceDetailViewModel>(relaxed = true)
    private val sheetState = mockk<ModalBottomSheetState>(relaxed = true)

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
        // The callback closes the details inside the view model's scope; run that scope inline.
        every { viewModel.coroutineContext } returns Dispatchers.Unconfined
    }

    @Test
    fun transferActionsForwardTheShownTransfer() {
        val state = transfer()
        val callback = TransactionDetailsBottomSheetCallback(viewModel, state, sheetState)

        callback.onHashClick()
        callback.onFromClick()
        callback.onToClick()

        verify { viewModel.transactionHashClicked(state) }
        verify { viewModel.fromClicked(state) }
        verify { viewModel.toClicked(state) }
    }

    @Test
    fun everyCloseActionClosesTheDetails() {
        val callback = TransactionDetailsBottomSheetCallback(viewModel, transfer(), sheetState)

        callback.onNavigationClick()
        callback.onBackClick()
        callback.onCloseClick()

        verify(exactly = 3) { viewModel.onDetailsClose() }
    }

    @Test
    fun swapActionsUseTheSwapHash() {
        val callback = TransactionDetailsBottomSheetCallback(viewModel, swap(), sheetState)

        callback.onItemClick(SWAP_HASH_ITEM)
        callback.onItemClick(OTHER_ITEM)
        callback.onSubscanClick()
        callback.onShareClick()

        verify(exactly = 1) { viewModel.onSwapDetailsHashClick(SWAP_HASH) }
        verify { viewModel.onSwapDetailsSubscanClicked(SWAP_HASH) }
        verify { viewModel.onShareSwapClicked(SWAP_HASH) }
    }

    @Test
    fun swapOnlyActionsRefuseATransfer() {
        val callback = TransactionDetailsBottomSheetCallback(viewModel, transfer(), sheetState)

        callback.onItemClick(OTHER_ITEM)
        assertThrows(IllegalArgumentException::class.java) { callback.onItemClick(SWAP_HASH_ITEM) }
        assertThrows(IllegalArgumentException::class.java) { callback.onSubscanClick() }
        assertThrows(IllegalArgumentException::class.java) { callback.onShareClick() }

        verify(exactly = 0) { viewModel.onSwapDetailsHashClick(any()) }
        verify(exactly = 0) { viewModel.onSwapDetailsSubscanClicked(any()) }
        verify(exactly = 0) { viewModel.onShareSwapClicked(any()) }
    }

    @Test
    fun transferSheetShowsTheTransferAndClosesFromItsToolbar() {
        render(transfer())

        compose.onNodeWithText(string(R.string.common_details)).assertExists()
        compose.onNodeWithText(TRANSFER_HASH).assertExists()
        compose.onNodeWithText("5Recipient").assertExists()
        compose.onNodeWithContentDescription(string(R.string.ux_close)).performClick()
        compose.waitForIdle()

        verify { viewModel.onDetailsClose() }
    }

    @Test
    fun swapSheetOpensSubscanAndShareAndCloses() {
        render(swap())

        compose.onNodeWithText(string(R.string.polkaswap_preview_title)).assertExists()
        compose.onNodeWithText(SWAP_HASH).assertExists()
        compose.onNodeWithText(Chain.Explorer.Type.SUBSCAN.capitalizedName).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.common_share)).performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithContentDescription(string(R.string.ux_close)).performClick()
        compose.waitForIdle()

        verify { viewModel.onSwapDetailsSubscanClicked(SWAP_HASH) }
        verify { viewModel.onShareSwapClicked(SWAP_HASH) }
        verify { viewModel.onDetailsClose() }
    }

    private fun render(state: TransactionDetailsState) {
        val callback = TransactionDetailsBottomSheetCallback(viewModel, state, sheetState)
        compose.setContent {
            FearlessAppTheme {
                TransactionDetailsBottomSheet(state, callback)
            }
        }
    }

    private fun transfer() = TransferDetailsState(
        hash = TextInputViewState(text = TRANSFER_HASH, hint = "Hash", endIcon = R.drawable.ic_more_vertical, isActive = false),
        firstAddress = AddressDisplayState("From", "5Sender", jp.co.soramitsu.common.R.drawable.ic_address_placeholder, R.drawable.ic_more_vertical),
        secondAddress = AddressDisplayState("To", "5Recipient", jp.co.soramitsu.common.R.drawable.ic_address_placeholder, R.drawable.ic_more_vertical),
        status = OperationStatusAppearance.COMPLETED,
        items = listOf(TitleValueViewState(title = "Amount", value = "1.5 DOT"))
    )

    private fun swap() = SwapDetailState(
        fromTokenImage = GradientIconState.Remote("", ""),
        toTokenImage = GradientIconState.Remote("", ""),
        fromTokenAmount = "1",
        toTokenAmount = "2",
        fromTokenName = "VAL",
        toTokenName = "XSTUSD",
        statusAppearance = SwapStatusAppearance.COMPLETED,
        address = "cnSwapper",
        addressName = null,
        hash = SWAP_HASH,
        fromTokenOnToToken = "2",
        liquidityProviderFee = "0.0007",
        networkFee = "0.0007",
        time = 1_675_834_923_575L,
        market = Market.SMART,
        isShowSubscanButtons = true
    )

    private companion object {
        const val TRANSFER_HASH = "0xtransferhash"
        const val SWAP_HASH = "0xswaphash"
        const val SWAP_HASH_ITEM = 1
        const val OTHER_ITEM = 2
    }
}

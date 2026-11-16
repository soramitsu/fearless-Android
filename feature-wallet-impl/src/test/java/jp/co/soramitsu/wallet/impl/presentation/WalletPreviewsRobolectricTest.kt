package jp.co.soramitsu.wallet.impl.presentation

import org.junit.Before
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.reflect.getDeclaredComposableMethod
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import androidx.compose.ui.test.hasText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Every `@Preview` of the wallet feature renders its fixture state on the JVM. The previews are the design-time samples of
 * the screens; one that throws or renders nothing is a broken screen for that state.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class WalletPreviewsRobolectricTest(
    private val fileClass: String,
    private val previewName: String
) {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    @Test
    fun previewRendersVisibleText() {
        val method = Class.forName("$PACKAGE.$fileClass").getDeclaredComposableMethod(previewName)
        method.asMethod().isAccessible = true

        // Preview fixtures are static snapshots; advance explicitly so decorative motion cannot
        // hold Espresso's idle loop open while checking the screen's semantics.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FearlessAppTheme { method.invoke(currentComposer, null) }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        val texts = compose.onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("$previewName rendered no text", texts.isNotEmpty())
    }

    companion object {
        private const val PACKAGE = "jp.co.soramitsu.wallet.impl.presentation"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun previews(): List<Array<String>> = listOf(
            arrayOf("contacts.ContactsContentKt", "PreviewContactsContent"),
            arrayOf("transaction.detail.swap.SwapDetailContentKt", "SwapDetailContentPreview"),
            arrayOf("transaction.detail.transfer.TransferDetailsScreenKt", "TransferDetailsPreview"),
            arrayOf("cross_chain.setup.CrossChainSetupContentKt", "CrossChainPreview"),
            arrayOf("balance.walletselector.SelectWalletContentKt", "SelectWalletScreenPreview"),
            arrayOf("balance.assetDetails.sort.AssetDetailsSortScreenKt", "AssetDetailsSortContentPreview"),
            arrayOf("balance.assetDetails.AssetDetailsScreenKt", "AssetDetailsToolbarPreview"),
            arrayOf("balance.assetDetails.AssetDetailsScreenKt", "AssetDetailsContentPreview"),
            arrayOf("balance.assetDetails.AssetDetailsScreenKt", "AssetDetailsItemPreview"),
            arrayOf("balance.searchAssets.SearchAssetsScreenKt", "PreviewWalletScreen"),
            arrayOf("balance.detail.TransactionItemKt", "PreviewTransactionItem"),
            arrayOf("balance.detail.BalanceDetailsScreenKt", "PreviewBalanceDetailScreenContent"),
            arrayOf("balance.assetselector.AssetSelectContentKt", "SelectAssetScreenPreview"),
            arrayOf("balance.detail.claimreward.ClaimRewardsContentKt", "ClaimRewardsPreview"),
            arrayOf("balance.optionswallet.OptionsWalletContentKt", "OptionsWalletScreenPreview"),
            arrayOf("balance.list.WalletScreenKt", "PreviewWalletScreen"),
            arrayOf("balance.chainselector.ChainSelectContentKt", "SelectChainScreenPreview"),
            arrayOf("manageassets.ManageAssetsContentKt", "ManageAssetsScreenPreview"),
            arrayOf("common.NetworkIssueKt", "NetworkIssuePreview"),
            arrayOf("receive.ReceiveScreenKt", "ReceiveScreenPreview"),
            arrayOf("history.AddressHistoryContentKt", "PreviewAddressHistoryContent"),
            arrayOf("addressbook.CreateContactContentKt", "PreviewCreateContactContent"),
            arrayOf("send.confirm.ConfirmSendContentKt", "ConfirmSendPreview"),
            arrayOf("send.setup.SendSetupContentKt", "SendSetupPreview")
        )
    }
}

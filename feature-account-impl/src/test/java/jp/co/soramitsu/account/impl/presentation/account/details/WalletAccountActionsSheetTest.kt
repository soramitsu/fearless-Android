package jp.co.soramitsu.account.impl.presentation.account.details

import android.app.Activity
import jp.co.soramitsu.account.impl.testutil.clickRow
import jp.co.soramitsu.account.impl.testutil.rowLabels
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.titleText
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WalletAccountActionsSheetTest {

    private val calls = mutableListOf<String>()

    private fun sheet(payload: WalletAccountActionsSheet.Payload): WalletAccountActionsSheet {
        val activity = Robolectric.buildActivity(Activity::class.java)
            .apply { get().setTheme(R.style.Theme_Soramitsu_Fearless) }.setup().get()
        return WalletAccountActionsSheet(
            context = activity,
            content = payload,
            onCopy = { calls += "copy:$it" },
            onExternalView = { calls += "view:$it" },
            onExportAccount = { calls += "export:$it" },
            onSwitchNode = { calls += "switch:$it" }
        ).also { it.show() }
    }

    @Test
    fun chainAccountOffersExportSwitchCopyAndExplorers() {
        val sheet = sheet(
            WalletAccountActionsSheet.Payload(
                value = "5Grwva",
                explorers = mapOf(Chain.Explorer.Type.SUBSCAN to "https://subscan.example/5Grwva"),
                chainId = "polkadot",
                chainName = "Polkadot"
            )
        )
        val subscan = string(R.string.view_in, Chain.Explorer.Type.SUBSCAN.capitalizedName)

        assertEquals("Polkadot", sheet.titleText())
        assertEquals(
            listOf(string(R.string.account_export), string(R.string.switch_node), string(R.string.common_copy_address), subscan),
            sheet.rowLabels()
        )

        sheet.clickRow(string(R.string.account_export))
        sheet.clickRow(string(R.string.switch_node))
        sheet.clickRow(string(R.string.common_copy_address))
        sheet.clickRow(subscan)

        assertEquals(listOf("export:polkadot", "switch:polkadot", "copy:5Grwva", "view:https://subscan.example/5Grwva"), calls)
        // Every row closes the sheet after acting.
        assertFalse(sheet.isShowing)
    }

    @Test
    fun nodeSwitchIsHiddenWhenTheChainCannotSwitch() {
        val sheet = sheet(WalletAccountActionsSheet.Payload("0xabc", emptyMap(), chainId = "eth", chainName = "Ethereum", canSwitchNode = false))

        assertEquals(listOf(string(R.string.account_export), string(R.string.common_copy_address)), sheet.rowLabels())
    }

    @Test
    fun walletWithoutChainOnlyCopiesAndKeepsTheAddressTitleUntilTheChainNameIsMissing() {
        val sheet = sheet(WalletAccountActionsSheet.Payload("0xabc", emptyMap()))

        assertEquals(listOf(string(R.string.common_copy_address)), sheet.rowLabels())
        // The chain title (none here) replaces the address title set by the base sheet.
        assertEquals("", sheet.titleText())
    }
}

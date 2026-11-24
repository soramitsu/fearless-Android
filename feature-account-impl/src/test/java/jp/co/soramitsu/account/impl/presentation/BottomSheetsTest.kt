package jp.co.soramitsu.account.impl.presentation

import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.account.impl.presentation.account.details.WalletAccountActionsSheet
import jp.co.soramitsu.account.impl.presentation.exporting.json.confirm.JsonExportTypeSheet
import jp.co.soramitsu.account.impl.presentation.importing.SubstratePasswordAccountModel
import jp.co.soramitsu.account.impl.presentation.importing.SubstratePasswordChooserBottomSheetDialog
import jp.co.soramitsu.account.impl.presentation.importing.source.view.JsonPasteOptionsSheet
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.EncryptionTypeChooserBottomSheetDialog
import jp.co.soramitsu.account.impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/** The plain (non-Hilt) bottom sheets of the account screens, shown on a real window (d-vmviews). */
@RunWith(RobolectricTestRunner::class)
class BottomSheetsTest {
    private lateinit var context: ContextThemeWrapper

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        context = ContextThemeWrapper(activity, R.style.Theme_Soramitsu_Fearless)
    }

    private fun FixedListBottomSheet.items(): List<TextView> {
        val container = findViewById<ViewGroup>(R.id.fixedListSheetItemContainer)!!
        return (0 until container.childCount).mapNotNull { container.getChildAt(it).findViewById(R.id.itemExternalActionContent) }
    }

    private fun FixedListBottomSheet.title() = findViewById<TextView>(R.id.fixedListSheetTitle)!!.text.toString()

    private fun FixedListBottomSheet.tap(index: Int) {
        val container = findViewById<ViewGroup>(R.id.fixedListSheetItemContainer)!!
        (0 until container.childCount).map(container::getChildAt)
            .filter { it.findViewById<TextView>(R.id.itemExternalActionContent) != null }[index].performClick()
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun `the json paste sheet offers paste and file and closes after a choice`() {
        val calls = mutableListOf<String>()
        val sheet = JsonPasteOptionsSheet(context, { calls += "paste" }, { calls += "file" }).apply { show() }

        assertEquals(context.getString(R.string.recovery_json), sheet.title())
        assertEquals(listOf(context.getString(R.string.import_json_paste), context.getString(R.string.common_choose_file)), sheet.items().map { it.text.toString() })
        sheet.tap(1)
        assertEquals(listOf("file"), calls)
        assertFalse(sheet.isShowing)

        JsonPasteOptionsSheet(context, { calls += "paste" }, { calls += "file" }).apply { show() }.tap(0)
        assertEquals(listOf("file", "paste"), calls)
    }

    @Test
    fun `the json export sheet exports as text or as a file`() {
        val calls = mutableListOf<String>()
        val sheet = JsonExportTypeSheet(context, { calls += "text" }, { calls += "file" }).apply { show() }

        assertEquals(context.getString(R.string.recovery_source_type), sheet.title())
        assertEquals(listOf(context.getString(R.string.json_export_text_title), context.getString(R.string.json_export_file_title)), sheet.items().map { it.text.toString() })
        sheet.tap(0)
        JsonExportTypeSheet(context, { calls += "text" }, { calls += "file" }).apply { show() }.tap(1)
        assertEquals(listOf("text", "file"), calls)
    }

    @Test
    fun `the account actions sheet copies, opens explorers, exports and switches node for a chain account`() {
        val calls = mutableListOf<String>()
        val payload = WalletAccountActionsSheet.Payload(
            "addr-1", mapOf(Chain.Explorer.Type.SUBSCAN to "https://subscan/addr-1"), chainId = "polkadot", chainName = "Polkadot"
        )
        val sheet = WalletAccountActionsSheet(
            context, payload, { calls += "copy:$it" }, { calls += "view:$it" }, { calls += "export:$it" }, { calls += "node:$it" }
        ).apply { show() }

        assertEquals("Polkadot", sheet.title())
        assertEquals(4, sheet.items().size)
        assertEquals(context.getString(R.string.account_export), sheet.items()[0].text.toString())
        assertEquals(context.getString(R.string.switch_node), sheet.items()[1].text.toString())
        (0 until 4).forEach { index ->
            WalletAccountActionsSheet(
                context, payload, { calls += "copy:$it" }, { calls += "view:$it" }, { calls += "export:$it" }, { calls += "node:$it" }
            ).apply { show() }.tap(index)
        }
        assertEquals(listOf("export:polkadot", "node:polkadot", "copy:addr-1", "view:https://subscan/addr-1"), calls)
    }

    @Test
    fun `an offline account offers only copying and no node switch`() {
        val offline = WalletAccountActionsSheet.Payload("addr-2", emptyMap(), chainName = "Taira", canSwitchNode = false)
        val sheet = WalletAccountActionsSheet(context, offline, {}, {}, {}, {}).apply { show() }
        assertEquals(1, sheet.items().size)
        assertEquals(context.getString(R.string.common_copy_address), sheet.items()[0].text.toString())

        val noSwitch = WalletAccountActionsSheet.Payload("addr-3", emptyMap(), chainId = "kusama", canSwitchNode = false)
        assertEquals(2, WalletAccountActionsSheet(context, noSwitch, {}, {}, {}, {}).apply { show() }.items().size)
    }

    @Test
    fun `the encryption chooser lists the types, marks the selected one and reports a choice`() {
        val sr = CryptoTypeModel("Schnorrkel", CryptoType.SR25519)
        val ed = CryptoTypeModel("Edwards", CryptoType.ED25519)
        val chosen = mutableListOf<CryptoTypeModel>()
        val dialog = EncryptionTypeChooserBottomSheetDialog(context, DynamicListBottomSheet.Payload(listOf(sr, ed), ed)) { chosen += it }
        dialog.show()
        ShadowLooper.idleMainLooper()

        val list = dialog.findViewById<RecyclerView>(R.id.dynamicListSheetContent)!!
        list.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(2000, android.view.View.MeasureSpec.EXACTLY)
        )
        list.layout(0, 0, 1080, 2000)
        assertEquals(2, list.adapter!!.itemCount)
        val edRow = list.findViewHolderForAdapterPosition(1)!!.itemView
        assertEquals("Edwards", edRow.findViewById<TextView>(R.id.encryptionTv).text.toString())
        assertEquals(android.view.View.VISIBLE, edRow.findViewById<android.view.View>(R.id.rightIcon).visibility)
        assertEquals(android.view.View.INVISIBLE, list.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<android.view.View>(R.id.rightIcon).visibility)

        list.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(listOf(sr), chosen)
    }

    @Test
    fun `the substrate password chooser explains the choice and reports the picked account`() {
        val nfkd = SubstratePasswordAccountModel(SubstratePasswordRule.NFKD, "Fearless", "1nfkd")
        val nfc = SubstratePasswordAccountModel(SubstratePasswordRule.NFC, "Polkadot.js", "1nfc")
        val chosen = mutableListOf<SubstratePasswordAccountModel>()
        val dialog = SubstratePasswordChooserBottomSheetDialog(context, DynamicListBottomSheet.Payload(listOf(nfkd, nfc), nfkd)) { chosen += it }
        dialog.show()
        ShadowLooper.idleMainLooper()

        val title = dialog.findViewById<TextView>(R.id.dynamicListSheetTitle)!!
        assertEquals(context.getString(R.string.substrate_password_choice_title), title.text.toString())
        val container = title.parent as ViewGroup
        val description = container.getChildAt(container.indexOfChild(title) + 1) as TextView
        assertEquals(context.getString(R.string.substrate_password_choice_description), description.text.toString())

        val list = dialog.findViewById<RecyclerView>(R.id.dynamicListSheetContent)!!
        list.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(2000, android.view.View.MeasureSpec.EXACTLY)
        )
        list.layout(0, 0, 1080, 2000)
        val second = list.findViewHolderForAdapterPosition(1)!!.itemView
        assertEquals("Polkadot.js", second.findViewById<TextView>(R.id.substratePasswordAccountTitle).text.toString())
        assertEquals("1nfc", second.findViewById<TextView>(R.id.substratePasswordAccountAddress).text.toString())
        assertEquals(android.view.View.INVISIBLE, second.findViewById<android.view.View>(R.id.substratePasswordAccountSelected).visibility)
        assertEquals(
            android.view.View.VISIBLE,
            list.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<android.view.View>(R.id.substratePasswordAccountSelected).visibility
        )
        second.performClick()
        ShadowLooper.idleMainLooper()
        assertEquals(listOf(nfc), chosen)
    }
}

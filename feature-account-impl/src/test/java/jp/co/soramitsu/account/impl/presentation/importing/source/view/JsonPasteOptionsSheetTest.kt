package jp.co.soramitsu.account.impl.presentation.importing.source.view

import android.app.Activity
import jp.co.soramitsu.account.impl.testutil.clickRow
import jp.co.soramitsu.account.impl.testutil.rowLabels
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.titleText
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JsonPasteOptionsSheetTest {

    @Test
    fun offersPastingOrChoosingAFile() {
        val calls = mutableListOf<String>()
        val activity = Robolectric.buildActivity(Activity::class.java)
            .apply { get().setTheme(R.style.Theme_Soramitsu_Fearless) }.setup().get()
        val sheet = JsonPasteOptionsSheet(activity, onPaste = { calls += "paste" }, onOpenFile = { calls += "file" })
        sheet.show()

        assertEquals(string(R.string.recovery_json), sheet.titleText())
        assertEquals(listOf(string(R.string.import_json_paste), string(R.string.common_choose_file)), sheet.rowLabels())

        sheet.clickRow(string(R.string.import_json_paste))
        sheet.clickRow(string(R.string.common_choose_file))

        assertEquals(listOf("paste", "file"), calls)
    }
}

package jp.co.soramitsu.account.impl.presentation.exporting.json.confirm

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
class JsonExportTypeSheetTest {

    @Test
    fun offersTextOrFileExport() {
        val calls = mutableListOf<String>()
        val activity = Robolectric.buildActivity(Activity::class.java)
            .apply { get().setTheme(R.style.Theme_Soramitsu_Fearless) }.setup().get()
        val sheet = JsonExportTypeSheet(activity, onExportByText = { calls += "text" }, onExportByFile = { calls += "file" })
        sheet.show()

        assertEquals(string(R.string.recovery_source_type), sheet.titleText())
        assertEquals(listOf(string(R.string.json_export_text_title), string(R.string.json_export_file_title)), sheet.rowLabels())

        sheet.clickRow(string(R.string.json_export_file_title))
        sheet.clickRow(string(R.string.json_export_text_title))

        assertEquals(listOf("file", "text"), calls)
    }
}

package jp.co.soramitsu.account.impl.presentation.profile

import android.app.Activity
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
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
class ProfileActionsSheetTest {

    @Test
    fun accountsRowComesFirstAndEveryRowReachesItsCallback() {
        val calls = mutableListOf<String>()
        val activity = Robolectric.buildActivity(Activity::class.java)
            .apply { get().setTheme(R.style.Theme_Soramitsu_Fearless) }.setup().get()
        val sheet = ProfileActionsSheet(
            context = activity,
            content = ExternalAccountActions.Payload("5Profile", emptyMap()),
            onCopy = { calls += "copy:$it" },
            onExternalView = { calls += "view:$it" },
            onOpenAccounts = { calls += "accounts" }
        )
        sheet.show()

        assertEquals("5Profile", sheet.titleText())
        assertEquals(listOf(string(R.string.profile_accounts_title), string(R.string.common_copy_address)), sheet.rowLabels())

        sheet.clickRow(string(R.string.profile_accounts_title))
        sheet.clickRow(string(R.string.common_copy_address))

        assertEquals(listOf("accounts", "copy:5Profile"), calls)
    }
}

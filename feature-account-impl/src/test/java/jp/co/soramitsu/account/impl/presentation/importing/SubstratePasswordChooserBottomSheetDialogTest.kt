package jp.co.soramitsu.account.impl.presentation.importing

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SubstratePasswordChooserBottomSheetDialogTest {

    private val fearless = SubstratePasswordAccountModel(SubstratePasswordRule.NFKD, "Fearless Wallet", "5Fearless")
    private val extension = SubstratePasswordAccountModel(SubstratePasswordRule.AS_TYPED, "Browser extension", "5Extension")

    @Test
    fun listsEveryAccountMarksTheSelectedOneAndReturnsTheChoice() {
        val chosen = mutableListOf<SubstratePasswordAccountModel>()
        val activity = Robolectric.buildActivity(Activity::class.java)
            .apply { get().setTheme(R.style.Theme_Soramitsu_Fearless) }.setup().get()
        val dialog = SubstratePasswordChooserBottomSheetDialog(
            activity,
            DynamicListBottomSheet.Payload(listOf(fearless, extension), selected = fearless),
            onClicked = { chosen += it }
        )
        dialog.show()
        shadowOf(Looper.getMainLooper()).idle()

        val title = dialog.findViewById<TextView>(R.id.dynamicListSheetTitle)!!
        assertEquals(string(R.string.substrate_password_choice_title), title.text.toString())
        val header = title.parent as android.widget.LinearLayout
        val description = header.getChildAt(header.indexOfChild(title) + 1) as TextView
        assertEquals(string(R.string.substrate_password_choice_description), description.text.toString())

        val list = dialog.findViewById<RecyclerView>(R.id.dynamicListSheetContent)!!
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY))
        list.layout(0, 0, 1080, 2000)
        assertEquals(2, list.childCount)

        val rows = (0 until list.childCount).map(list::getChildAt)
        assertEquals("Fearless Wallet", rows[0].findViewById<TextView>(R.id.substratePasswordAccountTitle).text.toString())
        assertEquals("5Extension", rows[1].findViewById<TextView>(R.id.substratePasswordAccountAddress).text.toString())
        assertEquals(View.VISIBLE, rows[0].findViewById<View>(R.id.substratePasswordAccountSelected).visibility)
        assertEquals(View.INVISIBLE, rows[1].findViewById<View>(R.id.substratePasswordAccountSelected).visibility)

        rows[1].performClick()

        assertEquals(listOf(extension), chosen)
        assertFalse(dialog.isShowing)
    }
}

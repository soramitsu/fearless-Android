package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import jp.co.soramitsu.feature_wallet_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class BalanceDetailSheetsRobolectricTest {

    private val activity: FragmentActivity = Robolectric.buildActivity(FragmentActivity::class.java).also {
        it.get().setTheme(jp.co.soramitsu.common.R.style.Theme_Soramitsu_Fearless)
    }.setup().get()

    private val calls = mutableListOf<String>()

    @Test
    fun substrateOptionsOfferExportNodeCopyAndClaim() {
        val titles = listOf(R.string.account_export, R.string.switch_node, R.string.common_copy_address, R.string.pool_claim_reward)
            .map(activity::getString)

        titles.indices.forEach { index ->
            val sheet = BalanceDetailOptionsBottomSheet(
                activity, "5Address",
                onExportAccount = { calls += "export" },
                onSwitchNode = { calls += "node" },
                onCopy = { calls += "copy:$it" },
                onClaimReward = { calls += "claim" }
            )
            sheet.show()
            ShadowLooper.idleMainLooper()
            val items = items(sheet.findViewById(R.id.fixedListSheetItemContainer)!!)

            assertEquals(titles, items.map { texts(it).first() })
            items[index].performClick()
            ShadowLooper.idleMainLooper()
            assertFalse(sheet.isShowing)
        }

        assertEquals(listOf("export", "node", "copy:5Address", "claim"), calls)
    }

    @Test
    fun substrateOptionsWithoutClaimAndEthereumOptions() {
        val substrate = BalanceDetailOptionsBottomSheet(activity, "5Address", {}, {}, { calls += "copy:$it" }, null)
        substrate.show()
        ShadowLooper.idleMainLooper()
        assertEquals(3, items(substrate.findViewById(R.id.fixedListSheetItemContainer)!!).size)

        val ethereum = BalanceDetailEthereumOptionsBottomSheet(activity, "0xabc", onExportAccount = { calls += "export" }, onCopy = { calls += "copy:$it" })
        ethereum.show()
        ShadowLooper.idleMainLooper()
        val items = items(ethereum.findViewById(R.id.fixedListSheetItemContainer)!!)
        assertEquals(listOf(activity.getString(R.string.account_export), activity.getString(R.string.common_copy_address)), items.map { texts(it).first() })
        items[1].performClick()

        assertEquals(listOf("copy:0xabc"), calls)
        assertEquals(activity.getString(R.string.account_option), ethereum.findViewById<TextView>(R.id.fixedListSheetTitle)!!.text.toString())
    }

    private fun items(container: LinearLayout) = (0 until container.childCount).map { container.getChildAt(it) }.filter { it.isClickable }

    private fun texts(view: android.view.View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
}

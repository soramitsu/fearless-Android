package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Arrival
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Outcome
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Row
import jp.co.soramitsu.account.api.domain.UniversalWalletSlot
import jp.co.soramitsu.common.view.PrimaryButton
import jp.co.soramitsu.feature_wallet_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class UniversalPasswordNoticeSheetRobolectricTest {

    private val activity: FragmentActivity = Robolectric.buildActivity(FragmentActivity::class.java).also {
        it.get().setTheme(jp.co.soramitsu.common.R.style.Theme_Soramitsu_Fearless)
    }.setup().get()

    private var switched = 0
    private var closed = 0

    @Test
    fun rowsListOldAndNewAddressesWithArrivalsAndSwitchRunsOnce() {
        val sheet = sheet(
            listOf(
                Row(UniversalWalletSlot.SOLANA, "OldSol", "NewSol", arrival = Arrival(Arrival.Kind.OLD_ADDRESS_USED_AGAIN, "0.5 SOL")),
                Row(UniversalWalletSlot.TON, "OldTon", "NewTon", outcome = Outcome.IN_USE, arrival = Arrival(Arrival.Kind.NEW_ADDRESS, null))
            )
        )

        val texts = texts(sheet)
        assertTrue(texts.contains(UniversalWalletSlot.SOLANA.networkName))
        assertTrue(texts.contains(activity.getString(R.string.universal_password_notice_old, "OldSol")))
        assertTrue(texts.contains(activity.getString(R.string.universal_password_notice_new, "NewTon")))
        assertTrue(texts.contains(activity.getString(R.string.universal_password_notice_arrived_old)))
        assertTrue(texts.contains(activity.getString(R.string.universal_password_notice_balance, "0.5 SOL")))
        assertTrue(texts.contains(activity.getString(R.string.universal_password_notice_used_again)))
        assertTrue(texts.contains(activity.getString(R.string.universal_password_notice_arrived_new)))
        assertTrue(texts.contains(activity.getString(R.string.universal_password_notice_in_use)))
        assertEquals(activity.getString(R.string.universal_password_notice_switch), switchButton(sheet).text.toString())

        switchButton(sheet).performClick()
        switchButton(sheet).performClick()

        assertEquals(1, switched)
        assertEquals(activity.getString(R.string.universal_password_notice_checking), switchButton(sheet).text.toString())
        assertFalse(sheet.findViewById<Button>(R.id.universalPasswordLater)!!.isEnabled)

        sheet.showResult(
            listOf(
                Row(UniversalWalletSlot.SOLANA, "OldSol", "NewSol", outcome = Outcome.SWITCHED),
                Row(UniversalWalletSlot.TON, "OldTon", "NewTon", outcome = Outcome.STILL_FUNDED, arrival = Arrival(Arrival.Kind.OLD_ADDRESS, null))
            )
        )

        assertTrue(texts(sheet).contains(activity.getString(R.string.universal_password_notice_switched)))
        assertTrue(texts(sheet).contains(activity.getString(R.string.universal_password_notice_move_old)))
        assertEquals(activity.getString(R.string.common_ok), switchButton(sheet).text.toString())

        switchButton(sheet).performClick()
        ShadowLooper.idleMainLooper()

        assertFalse(sheet.isShowing)
        assertEquals(1, closed)
    }

    @Test
    fun laterClosesWithoutSwitching() {
        val sheet = sheet(listOf(Row(UniversalWalletSlot.TAIRA, "OldTaira", "NewTaira", outcome = Outcome.UNKNOWN)))

        assertTrue(texts(sheet).contains(activity.getString(R.string.universal_password_notice_unknown)))
        sheet.findViewById<Button>(R.id.universalPasswordLater)!!.performClick()
        ShadowLooper.idleMainLooper()

        assertEquals(0, switched)
        assertEquals(1, closed)
        assertEquals(42L, UniversalPasswordNoticeState(42L, emptyList()).metaId)
    }

    private fun sheet(rows: List<Row>) = UniversalPasswordNoticeSheet(activity, rows, onSwitch = { switched++ }, onClosed = { closed++ })
        .also {
            it.show()
            ShadowLooper.idleMainLooper()
        }

    private fun switchButton(sheet: UniversalPasswordNoticeSheet) = sheet.findViewById<PrimaryButton>(R.id.universalPasswordSwitch)!!

    private fun texts(sheet: UniversalPasswordNoticeSheet): List<String> {
        val container = sheet.findViewById<LinearLayout>(R.id.universalPasswordRows)!!
        return (0 until container.childCount).map { (container.getChildAt(it) as TextView).text.toString() }
    }
}

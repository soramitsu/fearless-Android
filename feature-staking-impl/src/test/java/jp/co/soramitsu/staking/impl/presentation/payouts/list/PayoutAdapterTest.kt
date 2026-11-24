package jp.co.soramitsu.staking.impl.presentation.payouts.list

import android.os.CountDownTimer
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingViewFixture
import jp.co.soramitsu.staking.impl.presentation.payouts.list.model.PendingPayoutModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowCountDownTimer

@RunWith(RobolectricTestRunner::class)
class PayoutAdapterTest : StakingViewFixture() {
    private val handler = mockk<PayoutAdapter.ItemHandler>(relaxed = true)
    private val soon = PendingPayoutModel("Alice", 1_500L, System.currentTimeMillis(), R.color.green, "10 DOT", "$20")
    private val later = PendingPayoutModel("Bob", 3 * 86_400_000L, System.currentTimeMillis(), R.color.white, "5 DOT", null)

    private fun recyclerWith(adapter: PayoutAdapter): RecyclerView {
        val recycler = RecyclerView(context)
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        )
        recycler.layout(0, 0, 1080, 1920)
        return recycler
    }

    private fun RecyclerView.row(position: Int): View = requireNotNull(findViewHolderForAdapterPosition(position)).itemView

    private fun View.text(id: Int) = findViewById<TextView>(id)

    @Test
    fun rowsShowValidatorAmountsAndReportTheirPosition() {
        val adapter = PayoutAdapter(handler)
        adapter.submitList(listOf(soon, later))
        val recycler = recyclerWith(adapter)

        val first = recycler.row(0)
        assertEquals("Alice", first.text(R.id.itemListElementTitleLeft).text.toString())
        assertEquals("10 DOT", first.text(R.id.itemListElementTitleRight).text.toString())
        assertEquals("$20", first.text(R.id.itemListElementDescriptionRight).text.toString())
        assertEquals(context.getColor(R.color.green), first.text(R.id.itemListElementDescriptionLeft).currentTextColor)
        val second = recycler.row(1)
        assertEquals("Bob", second.text(R.id.itemListElementTitleLeft).text.toString())
        assertEquals("", second.text(R.id.itemListElementDescriptionRight).text.toString())

        second.performClick()
        first.performClick()
        verify { handler.payoutClicked(1) }
        verify { handler.payoutClicked(0) }
    }

    @Test
    fun expiredTimerMarksThePayoutAsExpired() {
        val adapter = PayoutAdapter(handler)
        adapter.submitList(listOf(soon))
        val description = recyclerWith(adapter).row(0).text(R.id.itemListElementDescriptionLeft)
        val timer = description.getTag(jp.co.soramitsu.common.R.string.common_time_left) as CountDownTimer
        val shadowTimer = Shadow.extract<ShadowCountDownTimer>(timer)
        assertTrue(shadowTimer.hasStarted())

        shadowTimer.invokeFinish()

        assertEquals(context.getString(R.string.staking_payout_expired), description.text.toString())
        assertEquals(context.getColor(R.color.red), description.currentTextColor)
        assertNull(description.getTag(jp.co.soramitsu.common.R.string.common_time_left))
    }

    @Test
    fun resubmittingKnownPayoutsKeepsThemAndAddsNewOnes() {
        val adapter = PayoutAdapter(handler)
        adapter.submitList(listOf(soon))
        var committed = false
        adapter.submitList(listOf(soon, later)) { committed = true }
        val deadline = System.currentTimeMillis() + 5_000
        while (!committed && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }

        assertTrue(committed)
        assertEquals(listOf(soon, later), adapter.currentList)
    }
}

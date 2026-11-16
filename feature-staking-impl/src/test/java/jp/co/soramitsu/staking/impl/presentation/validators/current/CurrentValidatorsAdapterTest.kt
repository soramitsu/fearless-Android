package jp.co.soramitsu.staking.impl.presentation.validators.current

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.StakingViewFixture
import jp.co.soramitsu.staking.impl.presentation.validators.current.model.NominatedValidatorModel
import jp.co.soramitsu.staking.impl.presentation.validators.current.model.NominatedValidatorStatusModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CurrentValidatorsAdapterTest : StakingViewFixture() {
    private val handler = mockk<CurrentValidatorsAdapter.Handler>(relaxed = true)
    private val active = NominatedValidatorStatusModel(
        NominatedValidatorStatusModel.TitleConfig("Active (1)", R.color.green), "Your stake is allocated"
    )
    private val waiting = NominatedValidatorStatusModel(null, "Waiting for the next era")
    private val alice = NominatedValidatorModel(address("alice", "Alice"), "10 DOT", isOversubscribed = true, isSlashed = false)
    private val bob = NominatedValidatorModel(address("bob", null), null, isOversubscribed = false, isSlashed = true)

    private fun address(value: String, name: String?) = AddressModel(value, PictureDrawable(Picture()), name)

    private fun bound(adapter: CurrentValidatorsAdapter, position: Int): GroupedListHolder {
        val holder = adapter.onCreateViewHolder(FrameLayout(context), adapter.getItemViewType(position))
        adapter.onBindViewHolder(holder, position)
        return holder
    }

    private fun View.text(id: Int) = findViewById<TextView>(id)

    @Test
    fun groupHeadersShowTheirStatusOnlyWhenTheyHaveATitle() {
        val adapter = CurrentValidatorsAdapter(handler)
        adapter.submitList(listOf(active, alice, waiting, bob))

        val titled = bound(adapter, 0).itemView
        assertEquals("Active (1)", titled.text(R.id.itemCurrentValidatorGroupStatus).text.toString())
        assertEquals(View.VISIBLE, titled.text(R.id.itemCurrentValidatorGroupStatus).visibility)
        assertEquals(context.getColor(R.color.green), titled.text(R.id.itemCurrentValidatorGroupStatus).currentTextColor)
        assertEquals("Your stake is allocated", titled.text(R.id.itemCurrentValidatorGroupDescription).text.toString())

        val untitled = bound(adapter, 2).itemView
        assertEquals(View.GONE, untitled.text(R.id.itemCurrentValidatorGroupStatus).visibility)
        assertEquals("Waiting for the next era", untitled.text(R.id.itemCurrentValidatorGroupDescription).text.toString())
        assertNotEquals(adapter.getItemViewType(0), adapter.getItemViewType(1))
    }

    @Test
    fun validatorRowsShowNominationBadgesAndForwardInfoClicks() {
        val adapter = CurrentValidatorsAdapter(handler)
        adapter.submitList(listOf(active, alice, waiting, bob))

        val aliceRow = bound(adapter, 1).itemView
        assertEquals("Alice", aliceRow.text(R.id.itemCurrentValidatorName).text.toString())
        assertEquals("10 DOT", aliceRow.text(R.id.itemCurrentValidatorNominated).text.toString())
        assertEquals(View.VISIBLE, aliceRow.text(R.id.itemCurrentValidatorNominated).visibility)
        assertEquals(View.VISIBLE, aliceRow.findViewById<View>(R.id.itemCurrentValidatorBadge).visibility)
        assertEquals(View.GONE, aliceRow.findViewById<View>(R.id.currentValidatorSlashedIcon).visibility)
        aliceRow.findViewById<View>(R.id.itemCurrentValidatorInfo).performClick()
        verify { handler.infoClicked(alice) }

        val bobRow = bound(adapter, 3).itemView
        assertEquals("bob", bobRow.text(R.id.itemCurrentValidatorName).text.toString())
        assertEquals(View.GONE, bobRow.text(R.id.itemCurrentValidatorNominated).visibility)
        assertEquals(View.GONE, bobRow.findViewById<View>(R.id.itemCurrentValidatorBadge).visibility)
        assertEquals(View.VISIBLE, bobRow.findViewById<View>(R.id.currentValidatorSlashedIcon).visibility)
    }

    @Test
    fun updatedListRebindsOnlyValidatorsWhoseNominationChanged() {
        val adapter = CurrentValidatorsAdapter(handler)
        adapter.submitList(listOf(active, alice, waiting, bob))
        val changed = mutableListOf<Int>()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                (positionStart until positionStart + itemCount).forEach { changed += it }
            }
        })
        val aliceMore = NominatedValidatorModel(alice.addressModel, "20 DOT", isOversubscribed = true, isSlashed = false)
        val sameActive = active.copy()

        var committed = false
        adapter.submitList(listOf(sameActive, aliceMore, waiting, bob)) { committed = true }
        val deadline = System.currentTimeMillis() + 5_000
        while (!committed && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }

        assertTrue(committed)
        assertEquals(listOf(1), changed)
        assertEquals("20 DOT", bound(adapter, 1).itemView.text(R.id.itemCurrentValidatorNominated).text.toString())
    }
}

package jp.co.soramitsu.staking.impl.presentation.validators

import android.content.Context
import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.validators.change.ValidatorModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ValidatorsAdapterTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(),
        jp.co.soramitsu.common.R.style.Theme_Soramitsu_Fearless)
    private val handler = mockk<ValidatorsAdapter.ItemHandler>(relaxed = true)
    private val row = ValidatorModel("01", false, PictureDrawable(Picture()), "address", null, "Alice", null, mockk())

    @Test
    fun bindsIdentityAndForwardsRowAndInformationActions() {
        val adapter = ValidatorsAdapter(handler)
        adapter.submitList(listOf(row))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0, mutableListOf())
        assertEquals("Alice", holder.itemView.findViewById<TextView>(R.id.itemValidatorName).text.toString())
        holder.itemView.performClick()
        holder.itemView.findViewById<View>(R.id.itemValidatorInfo).performClick()
        verify { handler.validatorClicked(row) }
        verify { handler.validatorInfoClicked(row) }
    }

    @Test
    fun checkmarkVisibilityAndEditActionsFollowTheMode() {
        val adapter = ValidatorsAdapter(handler)
        adapter.submitList(listOf(row))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        val icon = holder.itemView.findViewById<View>(R.id.itemValidatorActionIcon)
        for ((checked, visibility) in listOf(null to View.GONE, false to View.INVISIBLE, true to View.VISIBLE)) {
            holder.bindIcon(ValidatorsAdapter.Mode.VIEW, row.copy(isChecked = checked), handler)
            assertEquals(visibility, icon.visibility)
        }
        adapter.modeChanged(ValidatorsAdapter.Mode.EDIT)
        adapter.onBindViewHolder(holder, 0, mutableListOf(ValidatorsAdapter.Mode.EDIT))
        assertEquals(View.VISIBLE, icon.visibility)
        icon.performClick()
        verify { handler.removeClicked(row) }
    }

    @Test
    fun scoringShowsOnlyTheFieldsSuppliedByTheModel() {
        val holder = ValidatorsAdapter(handler).onCreateViewHolder(FrameLayout(context), 0)
        val primary = holder.itemView.findViewById<TextView>(R.id.itemValidatorScoringPrimary)
        val secondary = holder.itemView.findViewById<TextView>(R.id.itemValidatorScoringSecondary)
        holder.bindScoring(row)
        assertEquals(View.GONE, primary.visibility)
        assertEquals(View.GONE, secondary.visibility)
        holder.bindScoring(row.copy(scoring = ValidatorModel.Scoring.OneField("12%")))
        assertEquals("12%", primary.text.toString())
        assertEquals(View.VISIBLE, primary.visibility)
        assertEquals(View.GONE, secondary.visibility)
        holder.bindScoring(row.copy(scoring = ValidatorModel.Scoring.TwoFields("100 DOT", "$200")))
        assertEquals("100 DOT", primary.text.toString())
        assertEquals("$200", secondary.text.toString())
        assertEquals(View.VISIBLE, secondary.visibility)
    }

    @Test
    fun diffPayloadUpdatesSelectionAndScoringWithoutReplacingTheRow() {
        val updated = row.copy(isChecked = true, scoring = ValidatorModel.Scoring.OneField("15%"))
        assertTrue(ValidatorDiffCallback.areItemsTheSame(row, updated))
        assertFalse(ValidatorDiffCallback.areItemsTheSame(row, updated.copy(address = "another")))
        assertTrue(ValidatorDiffCallback.areContentsTheSame(row, row.copy()))
        assertFalse(ValidatorDiffCallback.areContentsTheSame(row, updated))
        assertFalse(ValidatorDiffCallback.areContentsTheSame(row, row.copy(title = "Renamed")))
        val adapter = ValidatorsAdapter(handler)
        adapter.submitList(listOf(updated))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        holder.bind(row, handler, ValidatorsAdapter.Mode.VIEW)
        adapter.onBindViewHolder(holder, 0, mutableListOf(requireNotNull(ValidatorDiffCallback.getChangePayload(row, updated))))
        assertEquals(View.VISIBLE, holder.itemView.findViewById<View>(R.id.itemValidatorActionIcon).visibility)
        assertEquals("15%", holder.itemView.findViewById<TextView>(R.id.itemValidatorScoringPrimary).text.toString())
    }
}

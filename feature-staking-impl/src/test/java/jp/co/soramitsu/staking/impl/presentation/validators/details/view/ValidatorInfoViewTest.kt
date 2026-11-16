package jp.co.soramitsu.staking.impl.presentation.validators.details.view

import android.view.View
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewValidatorInfoBinding
import jp.co.soramitsu.staking.impl.presentation.StakingViewFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ValidatorInfoViewTest : StakingViewFixture() {
    @Test
    fun populatedStakeFieldsAndClickActionReflectValidatorData() {
        val view = ValidatorInfoView(context)
        val binding = ViewValidatorInfoBinding.bind(view)
        view.setTotalStakeValue("100 DOT")
        view.setTotalStakeValueFiat("$200")
        view.setCommissionValue("5%")
        view.setNominatorsCount("8", "16")
        view.setEstimatedRewardApy("12%")
        view.setStatus("Active", R.color.green)
        assertTrue(texts(view).containsAll(listOf("100 DOT", "$200", "5%", "12%", "Active",
            context.getString(R.string.staking_max_format, "8", "16"))))
        var clicks = 0
        view.setTotalStakeClickListener { clicks++ }
        binding.validatorTotalStakeView.performClick()
        assertEquals(1, clicks)
        view.setNominatorsCount("9", null)
        assertTrue(texts(binding.validatorNominatorsView).contains("9"))
        view.setCommissionValue(null)
        assertEquals(View.GONE, binding.validatorCommission.visibility)
        view.hideActiveStakeFields()
        assertEquals(View.GONE, binding.validatorTotalStakeView.visibility)
        assertEquals(View.GONE, binding.validatorNominatorsView.visibility)
        view.showActiveStakeFields()
        assertEquals(View.VISIBLE, binding.validatorTotalStakeView.visibility)
        assertEquals(View.VISIBLE, binding.validatorEstimatedReward.visibility)
    }

    @Test
    fun warningsAreRenderedBesideTheRelevantValidatorField() {
        val view = ValidatorInfoView(context)
        val binding = ViewValidatorInfoBinding.bind(view)
        for (warning in listOf(Error.OversubscribedUnpaid, Error.OversubscribedPaid, Error.Blocked)) {
            view.setErrors(listOf(warning))
            assertTrue(texts(binding.validatorStatusView).contains(context.getString(warning.errorDescription)))
        }
        view.setErrors(listOf(Error.Slashed))
        assertTrue(texts(binding.validatorNominatorsView).contains(context.getString(Error.Slashed.errorDescription)))
    }
}

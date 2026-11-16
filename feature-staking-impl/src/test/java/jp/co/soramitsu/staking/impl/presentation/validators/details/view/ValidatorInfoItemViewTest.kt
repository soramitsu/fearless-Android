package jp.co.soramitsu.staking.impl.presentation.validators.details.view

import android.view.View
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewValidatorInfoItemBinding
import jp.co.soramitsu.staking.impl.presentation.StakingViewFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ValidatorInfoItemViewTest : StakingViewFixture() {
    @Test
    fun attributesAndContentSettersPopulateLabelsAndIcons() {
        val attrs = Robolectric.buildAttributeSet().addAttribute(R.attr.validatorInfoItemTitle, "Identity")
            .addAttribute(R.attr.validatorInfoItemTitleIcon, "@drawable/ic_info_16")
            .addAttribute(R.attr.validatorInfoItemIcon, "@drawable/ic_status_indicator").build()
        val view = ValidatorInfoItemView(context, attrs)
        val binding = ViewValidatorInfoItemBinding.bind(view)
        assertEquals("Identity", binding.validatorIdentityTitle.text.toString())
        assertNotNull(binding.validatorIdentityTitle.compoundDrawables[2])
        assertNotNull(binding.validatorIdentityBody.compoundDrawables[2])
        view.setTitle("Status")
        view.setBody("Active")
        view.setBodyIconResource(R.drawable.ic_status_indicator, R.color.green)
        view.setDescription("Verified on chain", R.drawable.ic_info_16)
        view.setExtraOrHide("Additional details")
        view.showExtra()
        assertTrue(texts(view).containsAll(listOf("Status", "Active", "Verified on chain", "Additional details")))
        assertEquals(View.VISIBLE, binding.validatorsIdentityDescription.visibility)
        view.hideDescription()
        assertEquals(View.GONE, binding.validatorsIdentityDescription.visibility)
        view.showDescription()
        assertEquals(View.VISIBLE, binding.validatorsIdentityDescription.visibility)
        view.hideExtra()
        assertEquals(View.GONE, binding.validatorIdentityExtra.visibility)
    }

    @Test
    fun absentOptionalValuesHideTheirViewsAndCanBeRestored() {
        val view = ValidatorInfoItemView(context)
        val binding = ViewValidatorInfoItemBinding.bind(view)
        view.setBodyOrHide(null)
        assertEquals(View.GONE, view.visibility)
        view.setBodyOrHide("Present")
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals("Present", binding.validatorIdentityBody.text.toString())
        view.setExtraOrHide("Extra")
        assertEquals(View.VISIBLE, binding.validatorIdentityExtra.visibility)
        view.setExtraOrHide(null)
        assertEquals(View.GONE, binding.validatorIdentityExtra.visibility)
    }
}

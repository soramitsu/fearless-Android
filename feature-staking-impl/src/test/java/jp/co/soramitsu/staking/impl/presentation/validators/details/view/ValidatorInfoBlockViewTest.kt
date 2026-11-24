package jp.co.soramitsu.staking.impl.presentation.validators.details.view

import android.view.View
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewValidatorInfoBlockBinding
import jp.co.soramitsu.staking.impl.presentation.StakingViewFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ValidatorInfoBlockViewTest : StakingViewFixture() {
    @Test
    fun attributesAndVisibilityChangesControlExtraTextAndInformationIcon() {
        val attrs = Robolectric.buildAttributeSet().addAttribute(R.attr.validatorInfoTitle, "Stake")
            .addAttribute(R.attr.showExtra, "true").addAttribute(R.attr.showInfoIcon, "true").build()
        val view = ValidatorInfoBlockView(context, attrs)
        val binding = ViewValidatorInfoBlockBinding.bind(view)
        assertEquals("Stake", binding.validatorInfoBlockTitle.text.toString())
        assertEquals(View.VISIBLE, binding.validatorInfoBlockExtra.visibility)
        assertNotNull(binding.validatorInfoBlockTitle.compoundDrawables[2])
        view.setTitle("Rewards")
        view.setBody("10 DOT")
        view.setExtra("$20")
        assertTrue(texts(view).containsAll(listOf("Rewards", "10 DOT", "$20")))
        view.changeExtraVisibility(false)
        view.changeInfoIconVisibility(false)
        assertEquals(View.GONE, binding.validatorInfoBlockExtra.visibility)
        assertNull(binding.validatorInfoBlockTitle.compoundDrawables[2])
    }
}

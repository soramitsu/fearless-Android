package jp.co.soramitsu.staking.impl.presentation.payouts.detail

import android.view.View
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import jp.co.soramitsu.common.utils.formatDateFromMillis
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.staking.impl.presentation.payouts.model.PendingPayoutParcelable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PayoutDetailsFragmentTest : StakingFragmentFixture() {
    private val createdAt = 1_700_000_000_000L

    @Test
    fun bundleCarriesThePayoutUnderItsKey() {
        val payout = PendingPayoutParcelable(
            PendingPayoutParcelable.ValidatorInfoParcelable("validator", "Alice"),
            BigInteger.ONE, BigInteger.TEN, createdAt, 1_000L, closeToExpire = false
        )

        val bundle = PayoutDetailsFragment.getBundle(payout)

        assertSame(payout, bundle.getParcelable<PendingPayoutParcelable>(PayoutDetailsFragment.KEY_PAYOUT))
    }

    @Test
    fun showsThePayoutAndForwardsValidatorPayoutAndBackActions() {
        val vm = mockk<PayoutDetailsViewModel>(relaxed = true)
        every { vm.payoutDetails } returns MutableLiveData(PayoutDetailsModel(address("Alice"), createdAt, "Era #42", "10 DOT", "$20"))
        val fragment = PayoutDetailsFragment()

        HiltFragmentHarness.launch(fragment, vm)
        val root = fragment.requireView()

        assertEquals(createdAt.formatDateFromMillis(fragment.requireContext()),
            root.findViewById<TextView>(R.id.payoutDetailsDate).text.toString())
        assertEquals("Era #42", root.findViewById<TextView>(R.id.payoutDetailsEra).text.toString())
        assertEquals("10 DOT", root.findViewById<TextView>(R.id.payoutDetailsReward).text.toString())
        assertEquals("$20", root.findViewById<TextView>(R.id.payoutDetailsRewardFiat).text.toString())
        assertTrue(texts(root.findViewById(R.id.payoutDetailsValidator)).contains("Alice"))

        root.findViewById<View>(R.id.payoutDetailsValidator).performClick()
        root.findViewById<View>(R.id.payoutDetailsSubmit).performClick()
        root.findViewById<View>(R.id.payoutDetailsToolbar).findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()

        verify { vm.validatorExternalActionClicked() }
        verify { vm.payoutClicked() }
        verify { vm.backClicked() }
    }
}

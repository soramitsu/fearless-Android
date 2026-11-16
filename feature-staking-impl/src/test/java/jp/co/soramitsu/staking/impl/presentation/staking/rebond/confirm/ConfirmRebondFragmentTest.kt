package jp.co.soramitsu.staking.impl.presentation.staking.rebond.confirm

import android.view.View
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmRebondFragmentTest : StakingFragmentFixture() {
    @Test
    fun showsTheReviewedAmountAndOriginAndForwardsConfirmation() {
        val vm = mockk<ConfirmRebondViewModel>(relaxed = true)
        val origin = MutableLiveData<AddressModel>(address("My wallet"))
        every { vm.showNextProgress } returns MutableLiveData(false)
        every { vm.assetModelFlow } returns MutableLiveData(asset)
        every { vm.amount } returns "10"
        every { vm.amountFiatFLow } returns MutableLiveData("$20")
        every { vm.feeLiveData } returns MutableLiveData(FeeStatus.Error)
        every { vm.originAddressModelLiveData } returns origin
        
        val payload = ConfirmRebondPayload(BigDecimal.TEN, null)
        assertSame(payload, ConfirmRebondFragment.getBundle(payload).getParcelable<ConfirmRebondPayload>(ConfirmRebondFragment.PAYLOAD_KEY))
        val fragment = ConfirmRebondFragment()
        HiltFragmentHarness.launch(fragment, vm) { it.imageLoader = mockk(relaxed = true) }
        val root = fragment.requireView()
        assertTrue(texts(root).containsAll(listOf("DOT", "10", "$20", "My wallet", "100 DOT available")))
        root.findViewById<View>(R.id.confirmRebondOriginAccount).performClick()
        root.findViewById<View>(R.id.confirmRebondConfirm).performClick()
        verify { vm.originAccountClicked() }
        verify { vm.confirmClicked() }
        
    }
}

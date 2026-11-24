package jp.co.soramitsu.staking.impl.presentation.staking.bond.confirm

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
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmBondMoreFragmentTest : StakingFragmentFixture() {
    @Test
    fun showsTheReviewedAmountAndOriginAndForwardsConfirmation() {
        val vm = mockk<ConfirmBondMoreViewModel>(relaxed = true)
        val origin = MutableLiveData<AddressModel>(address("My wallet"))
        every { vm.showNextProgress } returns MutableLiveData(false)
        every { vm.assetModelFlow } returns MutableLiveData(asset)
        every { vm.amount } returns "10"
        every { vm.amountFiatFLow } returns MutableLiveData("$20")
        every { vm.feeStatusLiveData } returns MutableLiveData(loadedFee())
        every { vm.originAddressModelLiveData } returns origin
        every { vm.stakeCreatorBalanceFlow } returns MutableStateFlow("100 DOT available")
        val payload = ConfirmBondMorePayload(BigDecimal.TEN, BigDecimal.ONE, "account", null, null)
        assertSame(payload, ConfirmBondMoreFragment.getBundle(payload).getParcelable<ConfirmBondMorePayload>(PAYLOAD_KEY))
        val fragment = ConfirmBondMoreFragment()
        HiltFragmentHarness.launch(fragment, vm) { it.imageLoader = mockk(relaxed = true) }
        val root = fragment.requireView()
        assertTrue(texts(root).containsAll(listOf("DOT", "10", "$20", "My wallet", "100 DOT available")))
        root.findViewById<View>(R.id.confirmBondMoreOriginAccount).performClick()
        root.findViewById<View>(R.id.confirmBondMoreConfirm).performClick()
        verify { vm.originAccountClicked() }
        verify { vm.confirmClicked() }
        
    }
}

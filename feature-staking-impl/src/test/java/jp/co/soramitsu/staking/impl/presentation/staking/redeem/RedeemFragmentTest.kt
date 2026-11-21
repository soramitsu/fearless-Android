package jp.co.soramitsu.staking.impl.presentation.staking.redeem

import android.view.View
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RedeemFragmentTest : StakingFragmentFixture() {
    @Test
    fun displaysUnlockAmountAndBothAccountsBeforeConfirmation() {
        val vm = mockk<RedeemViewModel>(relaxed = true)
        every { vm.showNextProgress } returns MutableLiveData(false)
        every { vm.assetModelFlow } returns MutableLiveData(asset)
        every { vm.stakingUnlockAmount } returns MutableSharedFlow<String>(replay = 1).apply { tryEmit("10") }
        every { vm.enteredFiatAmountFlow } returns MutableLiveData("$20")
        every { vm.feeLiveData } returns MutableLiveData(FeeStatus.Error)
        every { vm.accountLiveData } returns MutableLiveData(Optional.of(address("My wallet")))
        every { vm.collatorLiveData } returns MutableLiveData(Optional.of(address("Alice")))
        val payload = RedeemPayload("collator", null)
        assertSame(payload, RedeemFragment.getBundle(payload).getParcelable<RedeemPayload>(PAYLOAD_KEY))
        val fragment = RedeemFragment()
        HiltFragmentHarness.launch(fragment, vm) { it.imageLoader = mockk(relaxed = true) }
        val root = fragment.requireView()
        assertTrue(texts(root).containsAll(listOf("DOT", "10", "$20", "My wallet", "Alice")))
        root.findViewById<View>(R.id.accountAddressView).performClick()
        root.findViewById<View>(R.id.redeemConfirm).performClick()
        verify { vm.originAccountClicked() }
        verify { vm.confirmClicked() }
    }
}

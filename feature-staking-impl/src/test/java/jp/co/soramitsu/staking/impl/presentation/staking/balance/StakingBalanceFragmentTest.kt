package jp.co.soramitsu.staking.impl.presentation.staking.balance

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.HiltHostApplication
import jp.co.soramitsu.staking.impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.wallet.api.presentation.model.AmountModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltHostApplication::class)
class StakingBalanceFragmentTest {
    @After
    fun closeHost() {
        HiltFragmentHarness.closeHosts()
    }

    private val vm = mockk<StakingBalanceViewModel>(relaxed = true)
    private val pending = MutableLiveData(false)
    private val blockStake = MutableLiveData(false)
    private val blockUnstake = MutableLiveData(false)
    private val redeemEnabled = MutableLiveData(true)

    private fun launch(): StakingBalanceFragment {
        every { vm.redeemTitle } returns null
        every { vm.pendingAction } returns pending
        every { vm.shouldBlockStakeMore } returns blockStake
        every { vm.shouldBlockUnstake } returns blockUnstake
        every { vm.redeemEnabledLiveData } returns redeemEnabled
        every { vm.unbondingModelsLiveData } returns MutableLiveData(emptyList())
        every { vm.unbondingEnabledLiveData } returns MutableLiveData(false)
        every { vm.stakingBalanceModelFlow } returns MutableStateFlow(StakingBalanceModel(
            AmountModel(BigDecimal.TEN, "10 DOT", "$20", R.string.wallet_balance_bonded),
            AmountModel(BigDecimal.ONE, "1 DOT", "$2", R.string.wallet_balance_unbonding_v1_9_0),
            AmountModel(BigDecimal("2"), "2 DOT", "$4", R.string.wallet_balance_redeemable)))
        val fragment = StakingBalanceFragment()
        HiltFragmentHarness.launch(fragment, vm)
        return fragment
    }

    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test
    fun rendersBalancesAndForwardsEnabledActions() {
        val fragment = launch()
        val root = fragment.requireView()
        assertTrue(texts(root).containsAll(listOf("10 DOT", "1 DOT", "2 DOT", "$20", "$2", "$4")))
        root.findViewById<View>(R.id.stakingBalanceActionsBondMore).performClick()
        root.findViewById<View>(R.id.stakingBalanceActionsUnbond).performClick()
        root.findViewById<View>(R.id.stakingBalanceActionsRedeem).performClick()
        verify { vm.bondMoreClicked() }
        verify { vm.unbondClicked() }
        verify { vm.redeemClicked() }
        assertEquals("collator", StakingBalanceFragment.getBundle("collator").getString(StakingBalanceFragment.KEY_COLLATOR_ADDRESS))
        fragment.parentFragmentManager.beginTransaction().remove(fragment).commitNow()
    }

    @Test
    fun pendingActionDisablesButtonsAndRestoresTheirIndividualEligibility() {
        val fragment = launch()
        val root = fragment.requireView()
        pending.value = true
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(root.findViewById<View>(R.id.stakingBalanceActionsBondMore).isEnabled)
        assertFalse(root.findViewById<View>(R.id.stakingBalanceActionsUnbond).isEnabled)
        assertFalse(root.findViewById<View>(R.id.stakingBalanceActionsRedeem).isEnabled)
        blockStake.value = true
        blockUnstake.value = true
        redeemEnabled.value = false
        pending.value = false
        assertFalse(root.findViewById<View>(R.id.stakingBalanceActionsBondMore).isEnabled)
        assertFalse(root.findViewById<View>(R.id.stakingBalanceActionsUnbond).isEnabled)
        assertFalse(root.findViewById<View>(R.id.stakingBalanceActionsRedeem).isEnabled)
        blockStake.value = false
        blockUnstake.value = false
        redeemEnabled.value = true
        assertTrue(root.findViewById<View>(R.id.stakingBalanceActionsBondMore).isEnabled)
        assertTrue(root.findViewById<View>(R.id.stakingBalanceActionsUnbond).isEnabled)
        assertTrue(root.findViewById<View>(R.id.stakingBalanceActionsRedeem).isEnabled)
        fragment.parentFragmentManager.beginTransaction().remove(fragment).commitNow()
    }
}

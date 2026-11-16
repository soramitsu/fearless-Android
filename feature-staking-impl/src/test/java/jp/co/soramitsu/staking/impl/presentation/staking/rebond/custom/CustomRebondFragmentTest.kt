package jp.co.soramitsu.staking.impl.presentation.staking.rebond.custom

import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import coil.ImageLoader
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.view.AmountView
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class CustomRebondFragmentTest : StakingFragmentFixture() {
    private val vm = mockk<CustomRebondViewModel>(relaxed = true)
    private val entered = MutableStateFlow("5")
    private val imageLoader = mockk<ImageLoader>(relaxed = true)

    private fun launch(fiat: String?): View {
        every { vm.showNextProgress } returns MutableLiveData(false)
        every { vm.assetModelFlow } returns MutableLiveData(asset)
        every { vm.enteredAmountFlow } returns entered
        every { vm.amountFiatFLow } returns MutableLiveData(fiat)
        every { vm.feeLiveData } returns MutableLiveData(FeeStatus.Error)
        val fragment = CustomRebondFragment()
        HiltFragmentHarness.launch(fragment, vm) { it.imageLoader = imageLoader }
        return fragment.requireView()
    }

    @Test
    fun showsTheUnbondingAssetAndKeepsTheAmountInSyncWithTheViewModel() {
        val root = launch("$10")
        val amount = root.findViewById<AmountView>(R.id.rebondAmount)

        assertTrue(texts(amount).containsAll(listOf("DOT", "100 DOT available", "$10")))
        assertEquals("5", amount.amountInput.text.toString())
        verify { imageLoader.enqueue(any()) }

        entered.value = "7.5"
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("7.5", amount.amountInput.text.toString())
        amount.amountInput.setText("9")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("9", entered.value)
        assertTrue(root.findViewById<View>(R.id.rebondContinue).isEnabled)
    }

    @Test
    fun missingFiatLeavesTheFiatFieldEmptyAndActionsReachTheViewModel() {
        val root = launch(null)
        val fiat = root.findViewById<AmountView>(R.id.rebondAmount)
            .findViewById<TextView>(jp.co.soramitsu.common.R.id.stakingAssetFiatAmount)

        root.findViewById<View>(R.id.rebondContinue).performClick()
        root.findViewById<View>(R.id.rebondToolbar).findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()

        assertEquals("", fiat.text.toString())
        verify { vm.confirmClicked() }
        verify { vm.backClicked() }
    }
}

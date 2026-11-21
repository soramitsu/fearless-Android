package jp.co.soramitsu.staking.impl.presentation.staking.unbond.select

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.HiltHostApplication
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import jp.co.soramitsu.wallet.api.presentation.model.AssetModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltHostApplication::class)
class SelectUnbondFragmentTest {
    @After
    fun closeHost() {
        HiltFragmentHarness.closeHosts()
    }

    private fun model(singleScreen: Boolean): SelectUnbondViewModel {
        val vm = mockk<SelectUnbondViewModel>(relaxed = true)
        every { vm.isInputFocused } returns MutableStateFlow(false)
        every { vm.enteredAmountFlow } returns MutableStateFlow("10")
        every { vm.showNextProgress } returns MutableLiveData(false)
        every { vm.assetModelFlow } returns MutableLiveData(AssetModel("chain", "asset", "", "DOT", "Available: 100 DOT"))
        every { vm.enteredFiatAmountFlow } returns MutableLiveData("$20")
        every { vm.feeLiveData } returns MutableLiveData(FeeStatus.Error)
        val icon = PictureDrawable(Picture())
        every { vm.accountLiveData } returns MutableLiveData(Optional.of(AddressModel("account", icon, "My wallet")))
        every { vm.collatorLiveData } returns MutableLiveData(Optional.of(AddressModel("collator", icon, "Alice")))
        every { vm.oneScreenConfirmation } returns singleScreen
        every { vm.lockupPeriodLiveData } returns MutableLiveData("28 days")
        every { vm.unbondHint } returns MutableLiveData("Unstaking takes 28 days")
        return vm
    }

    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test
    fun displaysAmountsAndAddressesBeforeContinuing() {
        val vm = model(false)
        val fragment = SelectUnbondFragment()
        val activity = HiltFragmentHarness.launch(fragment, vm) { it.imageLoader = mockk(relaxed = true) }
        val root = fragment.requireView()
        assertTrue(texts(root).containsAll(listOf("DOT", "My wallet", "Alice", "$20")))
        assertTrue(texts(root).contains("28 days"))
        assertTrue(texts(root).contains("Unstaking takes 28 days"))
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.unbondContinue).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.confirmUnbondLayout).visibility)
        root.findViewById<View>(R.id.unbondContinue).performClick()
        verify { vm.nextClicked() }
        activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
    }

    @Test
    fun singleScreenModeShowsConfirmationAndPreservesPayload() {
        val payload = SelectUnbondPayload("collator", true)
        assertSame(payload, SelectUnbondFragment.getBundle(payload).getParcelable<SelectUnbondPayload>(SelectUnbondFragment.PAYLOAD_KEY))
        val vm = model(true)
        val fragment = SelectUnbondFragment()
        val activity = HiltFragmentHarness.launch(fragment, vm) { it.imageLoader = mockk(relaxed = true) }
        val root = fragment.requireView()
        assertEquals(View.GONE, root.findViewById<View>(R.id.unbondContinue).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.unbondFee).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.confirmUnbondLayout).visibility)
        root.findViewById<View>(R.id.unbondConfirm).performClick()
        verify { vm.confirmClicked() }
        activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
    }
}

package jp.co.soramitsu.staking.impl.presentation.confirm

import android.view.View
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.HiltHostApplication
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltHostApplication::class)
class ConfirmStakingFragmentTest {
    @After
    fun closeHost() {
        HiltFragmentHarness.closeHosts()
    }


    @Before
    fun clock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun showsNominationsAndForwardsClicksToTheViewModel() {
        val vm = mockk<ConfirmStakingViewModel>(relaxed = true)
        every { vm.nominationsLiveData } returns MutableLiveData("3 of 16")
        every { vm.unstakingTime } returns MutableSharedFlow<String>(replay = 1).apply { tryEmit("28 days") }
        every { vm.eraHoursLength } returns MutableSharedFlow<String>(replay = 1).apply { tryEmit("6 hours") }
        every { vm.selectedCollatorLiveData } returns MutableLiveData(null)
        every { vm.rewardDestinationLiveData } returns MutableLiveData(null)
        every { vm.displayAmountLiveData } returns MutableLiveData(null)
        val fragment = ConfirmStakingFragment()

        HiltFragmentHarness.launch(fragment, vm) { it.imageLoader = mockk(relaxed = true) }
        val root = fragment.requireView()

        assertEquals("3 of 16", root.findViewById<TextView>(R.id.confirmStakeSelectedValidatorsCount).text.toString())
        assertEquals("28 days", root.findViewById<TextView>(R.id.confirmStakingUnstakingPeriodLength).text.toString())
        assertEquals("6 hours", root.findViewById<TextView>(R.id.confirmStakingEachEraLength).text.toString())
        assertEquals(View.GONE, root.findViewById<View>(R.id.confirmStakeRewardDestination).visibility)

        root.findViewById<View>(R.id.confirmStakeConfirm).performClick()
        root.findViewById<View>(R.id.confirmStakeSelectedValidators).performClick()
        verify { vm.confirmClicked() }
        verify { vm.nominationsClicked() }
    }
}

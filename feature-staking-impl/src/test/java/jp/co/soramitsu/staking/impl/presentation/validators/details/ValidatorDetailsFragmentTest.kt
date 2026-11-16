package jp.co.soramitsu.staking.impl.presentation.validators.details

import android.view.View
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.staking.impl.presentation.validators.details.model.IdentityModel
import jp.co.soramitsu.staking.impl.presentation.validators.details.model.ValidatorDetailsModel
import jp.co.soramitsu.staking.impl.presentation.validators.details.model.ValidatorStakeModel
import jp.co.soramitsu.staking.impl.presentation.validators.details.view.Error
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.shadows.ShadowDialog

class ValidatorDetailsFragmentTest : StakingFragmentFixture() {
    @Test
    fun activeIdentityAndStakeRenderAndTheirActionsReachTheViewModel() {
        val vm = mockk<ValidatorDetailsViewModel>(relaxed = true)
        val identity = IdentityModel("Alice", "Legal name", "https://example.com", "@alice", "a@example.com", "pgp", "image", "alice")
        val stake = ValidatorStakeModel("Active", R.color.green,
            ValidatorStakeModel.ActiveStakeModel("100 DOT", "$200", "8", "16", "12%"), ValidatorStakeModel.Prefs(false, "5%"))
        val details = MutableLiveData(ValidatorDetailsModel(stake, "validator-address", address("Alice").image, identity))
        val breakdown = MutableLiveData<Event<ValidatorStakeBottomSheet.Payload>>()
        every { vm.validatorDetails } returns details
        every { vm.errorFlow } returns MutableStateFlow(listOf(Error.Slashed))
        every { vm.totalStakeEvent } returns breakdown
        val fragment = ValidatorDetailsFragment()
        HiltFragmentHarness.launch(fragment, vm)
        val root = fragment.requireView()
        assertTrue(texts(root).containsAll(listOf("Alice", "validator-address", "100 DOT", "$200", "12%", "5%")))
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.validatorIdentity).visibility)
        root.findViewById<View>(R.id.validatorTotalStakeView).performClick()
        root.findViewById<View>(R.id.validatorAccountInfo).performClick()
        root.findViewById<View>(R.id.validatorIdentityEmailView).performClick()
        root.findViewById<View>(R.id.validatorIdentityWebView).performClick()
        root.findViewById<View>(R.id.validatorIdentityTwitterView).performClick()
        verify { vm.totalStakeClicked() }
        verify { vm.accountActionsClicked() }
        verify { vm.emailClicked() }
        verify { vm.webClicked() }
        verify { vm.twitterClicked() }
        breakdown.value = Event(ValidatorStakeBottomSheet.Payload("Own", "10 DOT", "$20", "Nominators", "90 DOT", "$180",
            "Total", "100 DOT", "$200"))
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        assertTrue(texts(dialog.window!!.decorView).containsAll(listOf("10 DOT", "90 DOT", "$180", "$200")))
        dialog.dismiss()
        details.value = ValidatorDetailsModel(ValidatorStakeModel("Unelected", R.color.gray2, null, null),
            "anonymous-address", address("Anonymous").image, null)
        assertEquals(View.GONE, root.findViewById<View>(R.id.validatorIdentity).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.validatorTotalStakeView).visibility)
        assertTrue(texts(root).contains("anonymous-address"))
        assertEquals("01", ValidatorDetailsFragment.getBundle("01").getString(ValidatorDetailsFragment.KEY_VALIDATOR))
    }
}

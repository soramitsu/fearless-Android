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
import jp.co.soramitsu.staking.impl.presentation.validators.details.model.CollatorDetailsModel
import jp.co.soramitsu.staking.impl.presentation.validators.details.model.IdentityModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorDetailsParcelModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.shadows.ShadowDialog

class CollatorDetailsFragmentTest : StakingFragmentFixture() {
    private fun details(identity: IdentityModel?) = CollatorDetailsModel("collator-address", address("Alice").image,
        identity, "Active", R.color.green, "8", "12%", "100 MOVR", "$200", "5 MOVR", "10 MOVR", "90 MOVR")

    @Test
    fun rendersCollatorBondingAndIdentityAndOpensTheStakeBreakdown() {
        val vm = mockk<CollatorDetailsViewModel>(relaxed = true)
        val identity = IdentityModel("Alice", "Legal", "https://example.com", null, "a@example.com", null, null, "alice")
        val data = MutableLiveData(details(identity))
        val breakdown = MutableLiveData<Event<ValidatorStakeBottomSheet.Payload>>()
        every { vm.collatorDetails } returns data
        every { vm.totalStakeEvent } returns breakdown
        val parcel = mockk<CollatorDetailsParcelModel>()
        assertSame(parcel, CollatorDetailsFragment.getBundle(parcel).getParcelable<CollatorDetailsParcelModel>(CollatorDetailsFragment.KEY_COLLATOR))
        val fragment = CollatorDetailsFragment()
        HiltFragmentHarness.launch(fragment, vm)
        val root = fragment.requireView()
        assertTrue(texts(root).containsAll(listOf("Alice", "collator-address", "100 MOVR", "$200", "5 MOVR", "10 MOVR", "90 MOVR")))
        assertEquals(View.GONE, root.findViewById<View>(R.id.collatorInfoProgress).visibility)
        root.findViewById<View>(R.id.collatorTotalStakeView).performClick()
        root.findViewById<View>(R.id.collatorAccountInfo).performClick()
        root.findViewById<View>(R.id.validatorIdentityEmailView).performClick()
        root.findViewById<View>(R.id.validatorIdentityWebView).performClick()
        root.findViewById<View>(R.id.validatorIdentityTwitterView).performClick()
        verify { vm.totalStakeClicked() }
        verify { vm.accountActionsClicked() }
        verify { vm.emailClicked() }
        verify { vm.webClicked() }
        verify { vm.twitterClicked() }
        breakdown.value = Event(ValidatorStakeBottomSheet.Payload("Own", "10 MOVR", null, "Delegators", "90 MOVR", null,
            "Total", "100 MOVR", null))
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        assertTrue(texts(dialog.window!!.decorView).containsAll(listOf("10 MOVR", "90 MOVR", "100 MOVR")))
        dialog.dismiss()
        data.value = details(null)
        assertEquals(View.GONE, root.findViewById<View>(R.id.collatorIdentity).visibility)
        assertTrue(texts(root).contains("collator-address"))
    }
}

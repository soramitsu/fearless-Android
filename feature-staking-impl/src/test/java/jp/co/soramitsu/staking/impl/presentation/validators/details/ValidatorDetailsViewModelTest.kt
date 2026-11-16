package jp.co.soramitsu.staking.impl.presentation.validators.details

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asFlow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.presentation.validators.LegacySelectionFixture
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.IdentityParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.NominatorParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorPrefsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorStakeParcelModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ValidatorDetailsViewModelTest : LegacySelectionFixture() {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val external = mockk<ExternalAccountActions.Presentation>(relaxed = true)
    private val links = mockk<AppLinksProvider>()
    private val registry = mockk<ChainRegistry>()
    private val identity = IdentityParcelModel("Alice", "Alice Ltd", "https://example.com", "@alice", "alice@example.com", "pgp", "image", "alice")

    private fun model(active: Boolean = true, identified: Boolean = true): ValidatorDetailsViewModel {
        val stake = if (active) ValidatorStakeParcelModel.Active(StakingLaneFixtures.planks(100), StakingLaneFixtures.planks(10),
            listOf(NominatorParcelModel(ByteArray(32), StakingLaneFixtures.planks(90))), BigDecimal("0.12"), false, false)
        else ValidatorStakeParcelModel.Inactive
        cache.value = mapOf(alpha.accountIdHex to ValidatorDetailsParcelModel(alpha.accountIdHex, stake,
            identity.takeIf { identified }, ValidatorPrefsParcelModel(BigDecimal("0.05"), false)))
        coEvery { relay.maxRewardedNominators() } returns 256
        coEvery { registry.getChain(any()) } returns StakingLaneFixtures.chain()
        every { links.getTwitterAccountUrl("alice") } returns "https://twitter.com/alice"
        return track(ValidatorDetailsViewModel(interactor, relay, router, icons, external, links, resources, registry,
            SavedStateHandle(mapOf(ValidatorDetailsFragment.KEY_VALIDATOR to alpha.accountIdHex))))
    }

    @Test
    fun activeValidatorDisplaysIdentityStakeAndExternalActions(): Unit = runBlocking {
        val vm = model()
        val details = vm.validatorDetails.asFlow().await()
        assertEquals("Alice", details.identity?.display)
        assertEquals("100 DOT", details.stake.activeStakeModel?.totalStake)
        assertFalse(details.stake.prefs!!.isBlocked)
        assertTrue(requireNotNull(vm.errorFlow.await()).isEmpty())
        val payload = slot<ExternalAccountActions.Payload>()
        vm.accountActionsClicked().join()
        verify { external.showExternalActions(capture(payload)) }
        assertEquals(details.address, payload.captured.value)
        vm.webClicked()
        vm.twitterClicked()
        vm.emailClicked()
        vm.backClicked()
        verify { external.showBrowser("https://example.com") }
        verify { external.showBrowser("https://twitter.com/alice") }
        assertEquals("alice@example.com", vm.openEmailEvent.value?.peekContent())
        verify { router.back() }
    }

    @Test
    fun stakeBreakdownSeparatesOwnAndNominatorBalancesWithFiat(): Unit = runBlocking {
        every { interactor.currentAssetFlow() } returns flowOf(StakingLaneFixtures.asset().copy(
            token = StakingLaneFixtures.token(fiatRate = BigDecimal("2"))))
        val vm = model()
        vm.totalStakeClicked()
        val payload = vm.totalStakeEvent.asFlow().await().peekContent()
        assertEquals("10 DOT", payload.ownStake)
        assertEquals("90 DOT", payload.nominatorsStake)
        assertEquals("100 DOT", payload.totalStake)
        assertNotNull(payload.ownStakeFiat)
        assertNotNull(payload.nominatorsStakeFiat)
        assertNotNull(payload.totalStakeFiat)
    }

    @Test
    fun inactiveAndUnidentifiedValidatorsHaveNoStakeBreakdownOrContactActions(): Unit = runBlocking {
        val vm = model(active = false, identified = false)
        vm.accountActionsClicked().join()
        verify(exactly = 0) { external.showExternalActions(any()) }
        val details = vm.validatorDetails.asFlow().await()
        assertNull(details.identity)
        assertNull(details.stake.activeStakeModel)
        vm.webClicked()
        vm.twitterClicked()
        vm.emailClicked()
        assertNull(vm.openEmailEvent.value)
        verify(exactly = 0) { external.showBrowser(any()) }
    }
}

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
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.rewards.SubqueryRewardCalculator
import jp.co.soramitsu.staking.impl.presentation.validators.LegacySelectionFixture
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.CollatorStakeParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.IdentityParcelModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollatorDetailsViewModelTest : LegacySelectionFixture() {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val external = mockk<ExternalAccountActions.Presentation>(relaxed = true)
    private val links = mockk<AppLinksProvider>()
    private val registry = mockk<ChainRegistry>()
    private val rewardFactory = mockk<RewardCalculatorFactory>()
    private val calculator = mockk<SubqueryRewardCalculator>()

    private fun model(status: CandidateInfoStatus = CandidateInfoStatus.ACTIVE, identified: Boolean = true): CollatorDetailsViewModel {
        val identity = IdentityParcelModel("Alice", null, "https://example.com", null, "alice@example.com", null, null, "alice")
        val parcel = CollatorDetailsParcelModel(alice.address, CollatorStakeParcelModel(status,
            StakingLaneFixtures.planks(10), 3, StakingLaneFixtures.planks(100), StakingLaneFixtures.planks(5), null),
            identity.takeIf { identified }, "")
        every { rewardFactory.createSubquery() } returns calculator
        coEvery { calculator.getApyFor(any()) } returns BigDecimal("0.12")
        coEvery { registry.getChain(any()) } returns StakingLaneFixtures.chain()
        every { links.getTwitterAccountUrl("alice") } returns "https://twitter.com/alice"
        return track(CollatorDetailsViewModel(interactor, router, icons, external, links, resources, registry, rewardFactory,
            SavedStateHandle(mapOf(CollatorDetailsFragment.KEY_COLLATOR to parcel))))
    }

    @Test
    fun detailsFormatBondsRewardsAndEveryCollatorStatus(): Unit = runBlocking {
        val statuses = listOf(CandidateInfoStatus.ACTIVE to R.string.staking_nominator_status_active,
            CandidateInfoStatus.IDLE to R.string.staking_collator_status_idle,
            CandidateInfoStatus.EMPTY to R.string.staking_collator_status_idle,
            CandidateInfoStatus.LEAVING(42) to R.string.staking_collator_status_leaving)
        for ((status, title) in statuses) {
            val details = model(status).collatorDetails.asFlow().await()
            assertEquals("s$title", details.statusText)
            assertEquals("100 DOT", details.totalStake)
            assertEquals("10 DOT", details.selfBonded)
            assertEquals("90 DOT", details.effectiveAmountBonded)
            assertEquals("5 DOT", details.minBond)
            assertEquals("3", details.delegations)
            assertEquals("0.12%", details.estimatedRewardsApr)
            assertEquals("Alice", details.identity?.display)
        }
    }

    @Test
    fun contactsExternalActionsAndStakeBreakdownUseTheSelectedCollator(): Unit = runBlocking {
        val vm = model()
        val details = vm.collatorDetails.asFlow().await()
        vm.totalStakeClicked()
        val stake = vm.totalStakeEvent.asFlow().await().peekContent()
        assertEquals("10 DOT", stake.ownStake)
        assertEquals("90 DOT", stake.nominatorsStake)
        assertEquals("100 DOT", stake.totalStake)
        assertNull(stake.totalStakeFiat)
        vm.webClicked()
        vm.twitterClicked()
        vm.emailClicked()
        verify { external.showBrowser("https://example.com") }
        verify { external.showBrowser("https://twitter.com/alice") }
        assertEquals("alice@example.com", vm.openEmailEvent.value?.peekContent())
        val action = slot<ExternalAccountActions.Payload>()
        vm.accountActionsClicked().join()
        verify { external.showExternalActions(capture(action)) }
        assertEquals(details.address, action.captured.value)
        vm.backClicked()
        verify { router.back() }
    }

    @Test
    fun absentIdentityAndUnloadedAddressDoNotProduceContactActions(): Unit = runBlocking {
        val vm = model(identified = false)
        vm.accountActionsClicked().join()
        verify(exactly = 0) { external.showExternalActions(any()) }
        assertNull(vm.collatorDetails.asFlow().await().identity)
        vm.webClicked()
        vm.twitterClicked()
        vm.emailClicked()
        assertNull(vm.openEmailEvent.value)
        verify(exactly = 0) { external.showBrowser(any()) }
    }
}

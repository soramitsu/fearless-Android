package jp.co.soramitsu.staking.impl.presentation.common.rewardDestination

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.core.models.Asset as CoreAsset

class RewardDestinationProviderCoverageTest {

    @get:Rule
    val instant = InstantTaskExecutorRule()

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val alice = StakingAccount(chain.addressOf(ByteArray(32) { 1 }), "Alice", false)
    private val bob = StakingAccount(chain.addressOf(ByteArray(32) { 2 }), "Bob", false)
    private val links = AppLinksProvider("terms", "privacy", "https://payouts", "https://x/%s", "https://controller", "https://moonbeam")
    private val interactor = mockk<StakingInteractor>()
    private val scenario = mockk<StakingScenarioInteractor>()
    private val icons = mockk<AddressIconGenerator>()
    private val shared = mockk<StakingSharedState>()
    private val display = mockk<AddressDisplayUseCase>()

    private fun provider(asset: CoreAsset = chainAsset): RewardDestinationProvider {
        every { shared.assetWithChain } returns flowOf(SingleAssetSharedState.AssetWithChain(StakingLaneFixtures.chain(asset), asset))
        coEvery { shared.chain() } returns chain
        coEvery { icons.createAddressIcon(any<ByteArray>(), any<Int>(), any<Int>()) } returns mockk<PictureDrawable>(relaxed = true)
        coEvery { interactor.getSelectedAccountProjection() } returns alice
        coEvery { interactor.getAccountProjectionsInSelectedChains() } returns listOf(alice, bob)
        return RewardDestinationProvider(mockk(relaxed = true), interactor, scenario, icons, links, shared, display, mockk())
    }

    /** Runs a click handler in its own scope and waits for everything it launched. */
    private suspend fun click(action: (CoroutineScope) -> Unit) {
        val job = Job()
        action(CoroutineScope(job))
        job.complete()
        job.join()
    }

    private suspend fun RewardDestinationProvider.current() = rewardDestinationModelFlow.first()

    private fun stash() = StakingState.Stash.None(chain, ByteArray(32) { 3 }, ByteArray(32) { 3 }, ByteArray(32) { 3 })

    @Test
    fun `payout uses the selected account as the destination`(): Unit = runBlocking {
        val provider = provider()
        assertSame(RewardDestinationModel.Restake, provider.current())

        click(provider::payoutClicked)

        val payout = provider.current() as RewardDestinationModel.Payout
        assertEquals(alice.address, payout.destination.address)
        assertEquals("Alice", payout.destination.name)
    }

    @Test
    fun `payout without a selected account keeps restaking`(): Unit = runBlocking {
        val provider = provider()
        coEvery { interactor.getSelectedAccountProjection() } returns null

        click(provider::payoutClicked)

        assertSame(RewardDestinationModel.Restake, provider.current())
    }

    @Test
    fun `destination chooser opens only for a payout and lists the network accounts`(): Unit = runBlocking {
        val provider = provider()
        click(provider::payoutTargetClicked)
        assertNull(provider.showDestinationChooserEvent.value)

        click(provider::payoutClicked)
        click(provider::payoutTargetClicked)

        val payload = requireNotNull(provider.showDestinationChooserEvent.value).peekContent()
        assertEquals(listOf(alice.address, bob.address), payload.data.map { it.address })
        assertEquals(alice.address, payload.selected?.address)
    }

    @Test
    fun `a chosen destination replaces the payout and restake switches back`(): Unit = runBlocking {
        val provider = provider()
        val chosen = AddressModel(bob.address, mockk(relaxed = true), "Bob")

        click { provider.payoutDestinationChanged(chosen, it) }
        assertSame(chosen, (provider.current() as RewardDestinationModel.Payout).destination)

        click(provider::restakeClicked)
        assertSame(RewardDestinationModel.Restake, provider.current())
    }

    @Test
    fun `learn more opens the link for the staking type`(): Unit = runBlocking {
        val relay = provider()
        click(relay::learnMoreClicked)
        assertEquals("https://payouts", relay.openBrowserEvent.value?.peekContent())

        val parachain = provider(StakingLaneFixtures.chainAsset(staking = CoreAsset.StakingType.PARACHAIN))
        click(parachain::learnMoreClicked)
        assertEquals("https://moonbeam", parachain.openBrowserEvent.value?.peekContent())

        val unsupported = provider(StakingLaneFixtures.chainAsset(staking = CoreAsset.StakingType.UNSUPPORTED))
        click(unsupported::learnMoreClicked)
        assertEquals("", unsupported.openBrowserEvent.value?.peekContent())
    }

    @Test
    fun `an active payout destination is loaded with its display name and changes are tracked`(): Unit = runBlocking {
        val provider = provider()
        val stash = stash()
        coEvery { scenario.getRewardDestination(stash) } returns RewardDestination.Payout(ByteArray(32) { 2 })
        coEvery { display.invoke(bob.address) } returns "Bob on chain"

        provider.loadActiveRewardDestination(stash)

        val payout = provider.current() as RewardDestinationModel.Payout
        assertEquals(bob.address, payout.destination.address)
        assertEquals("Bob on chain", payout.destination.name)
        assertFalse(provider.rewardDestinationChangedFlow.drop(1).first())

        click(provider::restakeClicked)
        assertTrue(provider.rewardDestinationChangedFlow.drop(1).first())
    }

    @Test
    fun `an active restake destination is loaded unchanged`(): Unit = runBlocking {
        val provider = provider()
        val stash = stash()
        coEvery { scenario.getRewardDestination(stash) } returns RewardDestination.Restake

        provider.loadActiveRewardDestination(stash)

        assertSame(RewardDestinationModel.Restake, provider.current())
        assertFalse(provider.rewardDestinationChangedFlow.drop(1).first())
    }

    @Test
    fun `sora cannot restake and switches to payout`(): Unit = runBlocking {
        val provider = provider(StakingLaneFixtures.chainAsset(chainId = soraMainChainId, symbol = "XOR", precision = 18))

        assertFalse(provider.canRestake.first())

        val payout = provider.current() as RewardDestinationModel.Payout
        assertEquals(alice.address, payout.destination.address)
    }

    @Test
    fun `other relay chains can restake and stay on restake`(): Unit = runBlocking {
        val provider = provider()

        assertTrue(provider.canRestake.first())
        assertSame(RewardDestinationModel.Restake, provider.current())
    }
}

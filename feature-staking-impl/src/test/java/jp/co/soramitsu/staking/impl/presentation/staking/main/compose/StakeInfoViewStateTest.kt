package jp.co.soramitsu.staking.impl.presentation.staking.main.compose

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

class StakeInfoViewStateTest {

    private lateinit var resourceManager: ResourceManager

    @Before
    fun setUp() {
        resourceManager = mock(ResourceManager::class.java)
        given(resourceManager.getString(R.string.staking_your_stake)).willReturn("Your stake")
        given(resourceManager.getString(R.string.staking_main_stake_balance_staked)).willReturn("Staked")
        given(resourceManager.getString(R.string.staking_total_rewards_v1_9_0)).willReturn("Rewarded")
        given(resourceManager.getString(R.string.staking_rewards_apr)).willReturn("Rewards APR")
    }

    @Test
    fun `relay default uses localized legacy labels and a recoverable inactive status`() {
        val state = StakeInfoViewState.RelayChainStakeInfoViewState.default(resourceManager)

        assertEquals("Your stake", state.title)
        assertEquals("Staked", state.staked.title)
        assertEquals("Rewarded", state.rewarded.title)
        assertEquals(null, state.staked.value)
        assertEquals(null, state.rewarded.value)
        assertTrue(state.status is StakeStatus.Inactive)
    }

    @Test
    fun `parachain default preserves an adversarial collator title without changing labels`() {
        val title = "<collator> 💥 \\u0000"
        val state = StakeInfoViewState.ParachainStakeInfoViewState.default(resourceManager, title)

        assertEquals(title, state.title)
        assertEquals("Staked", state.staked.title)
        assertEquals("Rewards APR", state.rewards.title)
        assertEquals(null, state.staked.value)
        assertEquals(null, state.rewards.value)
        assertTrue(state.status is StakeStatus.IdleCollator)
    }

    @Test
    fun `timer statuses always select countdown content before their extra message`() {
        val activeCollator = StakeStatus.ActiveCollator(Long.MAX_VALUE).trailingContent()
        val leavingCollator = StakeStatus.LeavingCollator(123L).trailingContent()
        val pool = StakeStatus.PoolActive(456L, hideZeroTimer = false).trailingContent()

        assertTrue(activeCollator is StakeStatusTrailingContent.Countdown)
        activeCollator as StakeStatusTrailingContent.Countdown
        assertEquals(MAX_STAKING_TIMER_MILLIS, activeCollator.timeLeft)
        assertEquals("Next round", activeCollator.extraMessage)
        assertEquals(false, activeCollator.hideZeroTimer)

        assertTrue(leavingCollator is StakeStatusTrailingContent.Countdown)
        leavingCollator as StakeStatusTrailingContent.Countdown
        assertEquals("Waiting execution", leavingCollator.extraMessage)
        assertEquals(true, leavingCollator.hideZeroTimer)

        assertTrue(pool is StakeStatusTrailingContent.Countdown)
        assertEquals("", (pool as StakeStatusTrailingContent.Countdown).extraMessage)
    }

    @Test
    fun `countdown content clamps zero and negative values while non-timers keep message routing`() {
        val negative = StakeStatus.ActiveCollator(Long.MIN_VALUE).trailingContent()
        val zero = StakeStatus.LeavingCollator(0L).trailingContent()
        val message = StakeStatus.Inactive("Era 7").trailingContent()
        val none = StakeStatus.IdleCollator().trailingContent()

        assertEquals(0L, (negative as StakeStatusTrailingContent.Countdown).timeLeft)
        assertEquals(0L, (zero as StakeStatusTrailingContent.Countdown).timeLeft)
        assertEquals("Era 7", (message as StakeStatusTrailingContent.Message).value)
        assertSame(StakeStatusTrailingContent.None, none)
    }
}

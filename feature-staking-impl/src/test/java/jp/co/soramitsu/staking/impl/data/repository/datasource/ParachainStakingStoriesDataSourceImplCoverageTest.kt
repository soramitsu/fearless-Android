package jp.co.soramitsu.staking.impl.data.repository.datasource

import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The four parachain staking story groups, each with two pages linking the Moonbeam staking guide. */
class ParachainStakingStoriesDataSourceImplCoverageTest {

    @Test
    fun `parachain stories explain staking collators delegators and rewards`() = runTest {
        val groups = ParachainStakingStoriesDataSourceImpl().getStoriesFlow().first()

        assertEquals(
            listOf(
                R.string.staking_story_staking_title,
                R.string.parachain_staking_story_collator_title,
                R.string.parachain_staking_story_delegator_title,
                R.string.parachain_staking_story_rewards_title
            ),
            groups.map { it.titleRes }
        )
        assertEquals(listOf("💰", "💎", "⛏", "🎁"), groups.map { it.iconSymbol })
        assertTrue(groups.all { group -> group.elements.size == 2 && group.elements.all { it.titleRes == group.titleRes } })
        assertTrue(groups.flatMap { it.elements }.all { it.url.orEmpty().startsWith("https://docs.moonbeam.network/learn/features/staking/") })
        assertEquals(
            listOf(R.string.parachain_staking_story_rewards_page_1, R.string.parachain_staking_story_rewards_page_2),
            groups.last().elements.map { it.bodyRes }
        )
        assertEquals("https://docs.moonbeam.network/learn/features/staking/#reward-distribution", groups.last().elements.first().url)
    }
}

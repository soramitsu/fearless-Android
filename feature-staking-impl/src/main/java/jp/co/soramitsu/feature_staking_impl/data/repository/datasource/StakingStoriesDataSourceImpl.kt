package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.coroutines.flow.flowOf

class StakingStoriesDataSourceImpl : StakingStoriesDataSource {

    override fun getStoriesFlow() = flowOf(
        listOf(
            StoryGroup.Staking(
                titleRes = R.string.staking_story_staking_title,
                iconSymbol = "\uD83D\uDCB0",
                elements = listOf(
                    StoryGroup.Story.Staking(
                        R.string.staking_story_staking_title,
                        R.string.staking_story_staking_page_1,
                        url = "https://wiki.polkadot.network/docs/en/learn-staking"
                    ),
                    StoryGroup.Story.Staking(
                        R.string.staking_story_staking_title,
                        R.string.staking_story_staking_page_2,
                        url = "https://wiki.polkadot.network/docs/en/learn-staking"
                    )
                )
            ),
            StoryGroup.Staking(
                titleRes = R.string.staking_story_nominator_title,
                iconSymbol = "\uD83D\uDC8E",
                elements = listOf(
                    StoryGroup.Story.Staking(
                        R.string.staking_story_nominator_title,
                        R.string.staking_story_nominator_page_1,
                        url = "https://wiki.polkadot.network/docs/en/learn-nominator"
                    ),
                    StoryGroup.Story.Staking(
                        R.string.staking_story_nominator_title,
                        R.string.staking_story_nominator_page_2,
                        url = "https://wiki.polkadot.network/docs/en/learn-nominator"
                    )
                )
            ),
            StoryGroup.Staking(
                titleRes = R.string.staking_story_validator_title,
                iconSymbol = "⛏",
                elements = listOf(
                    StoryGroup.Story.Staking(
                        R.string.staking_story_validator_title,
                        R.string.staking_story_validator_page_1,
                        url = "https://wiki.polkadot.network/docs/en/learn-validator"
                    ),
                    StoryGroup.Story.Staking(
                        R.string.staking_story_validator_title,
                        R.string.staking_story_validator_page_2,
                        url = "https://wiki.polkadot.network/docs/en/learn-validator"
                    )
                )
            ),
            StoryGroup.Staking(
                titleRes = R.string.staking_story_reward_title,
                iconSymbol = "\uD83C\uDF81",
                elements = listOf(
                    StoryGroup.Story.Staking(
                        R.string.staking_story_reward_title,
                        R.string.staking_story_reward_page_1,
                        url = "https://wiki.polkadot.network/docs/en/learn-simple-payouts"
                    ),
                    StoryGroup.Story.Staking(
                        R.string.staking_story_reward_title,
                        R.string.staking_story_reward_page_2,
                        url = "https://wiki.polkadot.network/docs/en/learn-simple-payouts"
                    )
                )
            ),
        )
    )
}

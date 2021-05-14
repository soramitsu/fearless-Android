package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.feature_staking_api.domain.model.StakingStory
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.coroutines.flow.flowOf

class StakingStoriesDataSourceImpl : StakingStoriesDataSource {

    override fun getStoriesFlow() = flowOf(
        listOf(
            StakingStory(
                titleRes = R.string.staking_story_staking_title,
                iconSymbol = "\uD83D\uDCB0",
                elements = listOf(
                    StakingStory.Element(
                        R.string.staking_story_staking_title,
                        R.string.staking_story_staking_page_1,
                        url = "https://wiki.polkadot.network/docs/en/learn-staking"
                    ),
                    StakingStory.Element(
                        R.string.staking_story_staking_title,
                        R.string.staking_story_staking_page_2,
                        url = "https://wiki.polkadot.network/docs/en/learn-staking"
                    )
                )
            ),
            StakingStory(
                titleRes = R.string.staking_story_nominator_title,
                iconSymbol = "\uD83D\uDC8E",
                elements = listOf(
                    StakingStory.Element(
                        R.string.staking_story_nominator_title,
                        R.string.staking_story_nominator_page_1,
                        url = "https://wiki.polkadot.network/docs/en/learn-nominator"
                    ),
                    StakingStory.Element(
                        R.string.staking_story_nominator_title,
                        R.string.staking_story_nominator_page_2,
                        url = "https://wiki.polkadot.network/docs/en/learn-nominator"
                    )
                )
            ),
            StakingStory(
                titleRes = R.string.staking_story_validator_title,
                iconSymbol = "⛏",
                elements = listOf(
                    StakingStory.Element(
                        R.string.staking_story_validator_title,
                        R.string.staking_story_validator_page_1,
                        url = "https://wiki.polkadot.network/docs/en/learn-validator"
                    ),
                    StakingStory.Element(
                        R.string.staking_story_validator_title,
                        R.string.staking_story_validator_page_2,
                        url = "https://wiki.polkadot.network/docs/en/learn-validator"
                    )
                )
            ),
            StakingStory(
                titleRes = R.string.staking_story_reward_title,
                iconSymbol = "\uD83C\uDF81",
                elements = listOf(
                    StakingStory.Element(
                        R.string.staking_story_reward_title,
                        R.string.staking_story_reward_page_1,
                        url = "https://wiki.polkadot.network/docs/en/learn-simple-payouts"
                    ),
                    StakingStory.Element(
                        R.string.staking_story_reward_title,
                        R.string.staking_story_reward_page_2,
                        url = "https://wiki.polkadot.network/docs/en/learn-simple-payouts"
                    )
                )
            ),
        )
    )
}

package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.feature_staking_api.domain.model.StakingStory
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StakingStoriesDataSourceImpl : StakingStoriesDataSource {

    override fun getStoriesFlow(): Flow<List<StakingStory>> {
        return flow {

            val firstStoryElements = mutableListOf<StakingStory.Element>().apply {
                add(
                    StakingStory.Element(
                        R.string.staking_story_element_title,
                        R.string.staking_story_element_description,
                        "https://fearlesswallet.io/"
                    )
                )
                add(
                    StakingStory.Element(
                        R.string.staking_story_element_pos_title,
                        R.string.staking_story_element_pos_description,
                        "https://fearlesswallet.io/"
                    )
                )
            }

            val stories = listOf(
                StakingStory(R.string.staking_story_title, "\uD83D\uDCB0", firstStoryElements),
                StakingStory(R.string.staking_story_nominator_title, "\uD83D\uDC8E", listOf<StakingStory.Element>()),
                StakingStory(R.string.staking_story_validator_title, "⛏", listOf<StakingStory.Element>()),
                StakingStory(R.string.staking_story_whats_new_title, "\uD83C\uDF81", listOf<StakingStory.Element>())
            )
            emit(stories)
        }
    }
}

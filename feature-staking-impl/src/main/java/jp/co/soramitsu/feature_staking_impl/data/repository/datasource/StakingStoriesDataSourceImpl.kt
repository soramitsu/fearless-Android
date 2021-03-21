package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.feature_staking_api.domain.model.StakingStory
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.StakingStoryModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StakingStoriesDataSourceImpl : StakingStoriesDataSource {

    override fun getStoriesFlow(): Flow<List<StakingStory>> {
        return flow {

            val firstStoryElements = mutableListOf<StakingStory.Element>().apply {
                add(StakingStory.Element("What is Staking?", "Test", "test url"))
            }

            val stories = listOf(
                StakingStory("What is Staking?", "\uD83D\uDCB0", firstStoryElements),
                StakingStory("Who is Nominator?", "\uD83D\uDC8E", listOf<StakingStory.Element>()),
                StakingStory("Who is Validator?", "⛏", listOf<StakingStory.Element>()),
                StakingStory("What\'s new?", "\uD83C\uDF81", listOf<StakingStory.Element>())
            )
            emit(stories)
        }
    }
}

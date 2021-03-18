package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.feature_staking_api.domain.model.StakingStory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StakingStoriesDataSourceImpl : StakingStoriesDataSource {

    override fun getStoriesFlow(): Flow<List<StakingStory>> {
        return flow {
            val stories = mutableListOf<StakingStory>().apply {
                add(StakingStory("What is Staking?", "\uD83D\uDCB0"))
                add(StakingStory("Who is Nominator?", "\uD83D\uDC8E"))
                add(StakingStory("Who is Validator?", "⛏"))
                add(StakingStory("What\'s new?", "\uD83C\uDF81"))
            }
            emit(stories)
        }
    }
}

package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.feature_staking_api.domain.model.StakingStory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StakingStoriesDataSourceImpl : StakingStoriesDataSource {

    override fun getStoriesFlow(): Flow<List<StakingStory>> {
        return flow {
            val stories = listOf(
                StakingStory("What is Staking?", "\uD83D\uDCB0"),
                StakingStory("Who is Nominator?", "\uD83D\uDC8E"),
                StakingStory("Who is Validator?", "⛏"),
                StakingStory("What\'s new?", "\uD83C\uDF81")
            )
            emit(stories)
        }
    }
}

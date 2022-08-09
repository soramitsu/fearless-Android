package jp.co.soramitsu.featurestakingimpl.data.repository.datasource

import jp.co.soramitsu.common.domain.model.StoryGroup
import kotlinx.coroutines.flow.Flow

interface StakingStoriesDataSource {

    fun getStoriesFlow(): Flow<List<StoryGroup.Staking>>
}

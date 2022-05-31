package jp.co.soramitsu.feature_staking_impl.data.repository

import java.math.BigInteger
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindTotalInsurance
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.StakingStoriesDataSource
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.queryNonNull
import kotlinx.coroutines.flow.Flow

class StakingRepositoryImpl(
    private val localStorage: StorageDataSource,
    private val stakingStoriesDataSource: StakingStoriesDataSource,
) : StakingRepository {

    override suspend fun getTotalIssuance(chainId: ChainId): BigInteger = localStorage.queryNonNull(
        keyBuilder = { it.metadata.balances().storage("TotalIssuance").storageKey() },
        binding = ::bindTotalInsurance,
        chainId = chainId
    )

    override fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>> {
        return stakingStoriesDataSource.getStoriesFlow()
    }
}

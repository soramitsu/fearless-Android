package jp.co.soramitsu.staking.impl.data.repository

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.queryNonNull
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindTotalIssuance
import jp.co.soramitsu.staking.impl.data.repository.datasource.StakingStoriesDataSource
import jp.co.soramitsu.wallet.api.data.cache.bindAccountInfoOrDefault
import kotlinx.coroutines.flow.Flow

class StakingRepositoryImpl(
    private val localStorage: StorageDataSource,
    private val remoteStorageSource: StorageDataSource,
    private val stakingStoriesDataSource: StakingStoriesDataSource
) : StakingRepository {

    override suspend fun getTotalIssuance(chainId: ChainId): BigInteger = localStorage.queryNonNull(
        keyBuilder = { it.metadata.balances().storage("TotalIssuance").storageKey() },
        binding = ::bindTotalIssuance,
        chainId = chainId
    )

    override suspend fun getAccountInfo(chainId: ChainId, accountId: AccountId): AccountInfo {
        return remoteStorageSource.queryNonNull(
            chainId = chainId,
            keyBuilder = { it.metadata.system().storage("Account").storageKey(it, accountId) },
            binding = ::bindAccountInfoOrDefault
        )
    }

    override fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>> {
        return stakingStoriesDataSource.getStoriesFlow()
    }
}

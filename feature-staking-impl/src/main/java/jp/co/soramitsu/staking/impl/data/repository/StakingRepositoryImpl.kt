package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.queryNonNull
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindTotalIssuance
import jp.co.soramitsu.wallet.api.data.cache.bindAccountInfoOrDefault
import java.math.BigInteger

class StakingRepositoryImpl(
    private val localStorage: StorageDataSource,
    private val remoteStorageSource: StorageDataSource
) : StakingRepository {

    override suspend fun getTotalIssuance(chainId: ChainId): BigInteger = localStorage.queryNonNull(
        keyBuilder = { it.metadata.balances().storage("TotalIssuance").storageKey() },
        binding = ::bindTotalIssuance,
        chainId = chainId
    )

    override suspend fun getAccountInfo(chainId: ChainId, accountId: AccountId): AccountInfo {
        return remoteStorageSource.query(
            chainId = chainId,
            keyBuilder = { it.metadata.system().storage("Account").storageKey(it, accountId) },
            binding = ::bindAccountInfoOrDefault
        )
    }
}

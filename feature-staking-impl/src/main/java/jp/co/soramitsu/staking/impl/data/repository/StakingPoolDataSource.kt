package jp.co.soramitsu.staking.impl.data.repository

import java.math.BigInteger
import jp.co.soramitsu.common.utils.nominationPools
import jp.co.soramitsu.common.utils.u32ArgumentFromStorageKey
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.queryNonNull
import jp.co.soramitsu.staking.impl.data.model.BondedPool
import jp.co.soramitsu.staking.impl.data.model.PoolMember
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindBondedPools
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindBondedPoolsMetadata
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindExistingPools
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindLastPoolId
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindMaxMembersInPool
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindMaxPoolMembers
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindMaxPools
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindMinCreateBond
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindMinJoinBond
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindPoolMember
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants

class StakingPoolDataSource(
    private val remoteStorage: StorageDataSource,
    private val localStorage: StorageDataSource,
    private val chainRegistry: ChainRegistry,
    private val walletConstants: WalletConstants
) {
    suspend fun minJoinBond(chainId: ChainId): BigInteger {
        return remoteStorage.queryNonNull(
            keyBuilder = { it.metadata.nominationPools().storage("MinJoinBond").storageKey() },
            binding = ::bindMinJoinBond,
            chainId = chainId
        )
    }

    suspend fun minCreateBond(chainId: ChainId): BigInteger {
        return remoteStorage.queryNonNull(
            keyBuilder = { it.metadata.nominationPools().storage("MinCreateBond").storageKey() },
            binding = ::bindMinCreateBond,
            chainId = chainId
        )
    }

    suspend fun maxPools(chainId: ChainId): BigInteger? {
        return remoteStorage.query(
            keyBuilder = { it.metadata.nominationPools().storage("MaxPools").storageKey() },
            binding = ::bindMaxPools,
            chainId = chainId
        )
    }

    suspend fun maxPoolMembers(chainId: ChainId): BigInteger? {
        return remoteStorage.query(
            keyBuilder = { it.metadata.nominationPools().storage("MaxPoolMembersPerPool").storageKey() },
            binding = { scale, runtime ->
                scale?.let { bindMaxPoolMembers(it, runtime) }
            },
            chainId = chainId
        )
    }

    suspend fun maxMembersInPool(chainId: ChainId): BigInteger? {
        return remoteStorage.query(
            keyBuilder = { it.metadata.nominationPools().storage("MaxPoolMembers").storageKey() },
            binding = ::bindMaxMembersInPool,
            chainId = chainId
        )
    }

    suspend fun existingPools(chainId: ChainId): BigInteger {
        return remoteStorage.queryNonNull(
            keyBuilder = { it.metadata.nominationPools().storage("CounterForBondedPools").storageKey() },
            binding = ::bindExistingPools,
            chainId = chainId
        )
    }

    suspend fun bondedPools(chainId: ChainId): Map<BigInteger, BondedPool?> {
        return remoteStorage.queryByPrefix(
            chainId = chainId,
            prefixKeyBuilder = { it.metadata.nominationPools().storage("BondedPools").storageKey() },
            keyExtractor = { it.u32ArgumentFromStorageKey() }
        ) { scale, runtime, _ ->
            scale?.let { bindBondedPools(it, runtime) }
        }
    }

    suspend fun poolsMetadata(chainId: ChainId): Map<BigInteger, String?> {
        return remoteStorage.queryByPrefix(
            chainId = chainId,
            prefixKeyBuilder = { it.metadata.nominationPools().storage("Metadata").storageKey() },
            keyExtractor = { it.u32ArgumentFromStorageKey() }
        ) { scale, runtime, _ ->
            scale?.let { bindBondedPoolsMetadata(it, runtime) }
        }
    }

    suspend fun lastPoolId(chainId: ChainId): BigInteger {
        return remoteStorage.queryNonNull(
            keyBuilder = { it.metadata.nominationPools().storage("LastPoolId").storageKey() },
            binding = ::bindLastPoolId,
            chainId = chainId
        )
    }

    suspend fun poolMembers(chainId: ChainId, accountId: AccountId): PoolMember? {
        return remoteStorage.query(
            keyBuilder = { it.metadata.nominationPools().storage("PoolMembers").storageKey(it, accountId) },
            binding = ::bindPoolMember,
            chainId = chainId
        )
    }
}

package jp.co.soramitsu.feature_staking_impl.scenarios.parachain

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.getList
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.data.network.runtime.binding.requireType
import jp.co.soramitsu.common.data.network.runtime.binding.returnType
import jp.co.soramitsu.common.utils.parachainStaking
import jp.co.soramitsu.common.utils.storageKeys
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.model.AtStake
import jp.co.soramitsu.feature_staking_api.domain.model.CandidateInfo
import jp.co.soramitsu.feature_staking_api.domain.model.Delegation
import jp.co.soramitsu.feature_staking_api.domain.model.DelegatorState
import jp.co.soramitsu.feature_staking_api.domain.model.Round
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.toDelegations
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindAtStakeOfCollator
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindCandidateInfo
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindDelegationScheduledRequests
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindDelegatorState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindRound
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindSelectedCandidates
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindStaked
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StakingParachainScenarioRepository(
    private val remoteStorage: StorageDataSource,
    private val localStorage: StorageDataSource,
) {

    fun stakingStateFlow(
        chain: Chain,
        accountId: AccountId
    ): Flow<StakingState> {
        return getDelegatorStateFlow(chain.id, accountId).map {
            when {
                it != null -> StakingState.Parachain.Delegator(chain, accountId, it.toDelegations(), it.total.toBigDecimal())
                else -> StakingState.Parachain.None(chain, accountId)
            }
        }
    }

    private fun getDelegatorStateFlow(chainId: ChainId, accountId: AccountId): Flow<DelegatorState?> {
        return remoteStorage.observe(
            chainId = chainId,
            keyBuilder = {
                it.metadata.parachainStaking().storage("DelegatorState").storageKey(it, accountId)
            },
            binder = { scale, runtime ->
                scale?.let { bindDelegatorState(it, runtime) }
            }
        )
    }

    suspend fun getDelegatorState(chainId: ChainId, accountId: AccountId) = localStorage.query(
        chainId = chainId,
        keyBuilder = {
            it.metadata.parachainStaking().storage("DelegatorState").storageKey(it, accountId)
        },
        binding = { scale, runtime ->
            scale?.let { bindDelegatorState(it, runtime) }
        }
    )

    suspend fun getAtStakeOfCollator(chainId: ChainId, collatorId: AccountId, currentRound: BigInteger): AtStake {
        return remoteStorage.query(
            chainId = chainId,
            keyBuilder = { runtime ->
                val storage = runtime.metadata.parachainStaking().storage("AtStake")
                storage.storageKey(runtime, currentRound, collatorId)
            },
            binding = { scale, runtime ->
                scale?.let { bindAtStakeOfCollator(it, runtime) } ?: incompatible()
            }
        )
    }

    suspend fun getStaked(chainId: ChainId, currentRound: BigInteger): BigInteger {
        return remoteStorage.query(
            chainId = chainId,
            keyBuilder = { runtime ->
                val storage = runtime.metadata.parachainStaking().storage("Staked")
                storage.storageKey(runtime, currentRound)
            },
            binding = { scale, runtime ->
                scale?.let { bindStaked(it, runtime) } ?: incompatible()
            }
        )
    }

    suspend fun getCandidateInfos(chainId: ChainId, addresses20: List<ByteArray>): AccountIdMap<CandidateInfo> {
        if (addresses20.isEmpty()) return emptyMap()
        return remoteStorage.queryKeys(
            chainId = chainId,
            keysBuilder = { runtime ->
                val storage = runtime.metadata.parachainStaking().storage("CandidateInfo")
                storage.storageKeys(
                    runtime = runtime,
                    singleMapArguments = addresses20,
                    argumentTransform = { it.toHexString() }
                )
            },
            binding = { scale, runtime ->
                scale?.let { bindCandidateInfo(it, runtime) } ?: incompatible()
            }
        )
    }

    suspend fun getCandidateInfo(chainId: ChainId, collatorId: ByteArray): CandidateInfo {
        return remoteStorage.query(
            chainId = chainId,
            keyBuilder = { runtime ->
                val storage = runtime.metadata.parachainStaking().storage("CandidateInfo")
                storage.storageKey(
                    runtime = runtime, collatorId
                )
            },
            binding = { scale, runtime ->
                scale?.let { bindCandidateInfo(it, runtime) } ?: incompatible()
            }
        )
    }

    suspend fun getCurrentRound(chainId: ChainId): Round {
        return remoteStorage.query(
            chainId,
            keyBuilder = { runtime ->
                runtime.metadata.parachainStaking().storage("Round").storageKey()
            },
            binding = { scale, runtime ->
                scale?.let { bindRound(it, runtime) } ?: incompatible()
            }
        )
    }

    suspend fun getSelectedCandidates(chainId: ChainId) = remoteStorage.query(
        chainId = chainId,
        keyBuilder = { it.metadata.parachainStaking().storage("SelectedCandidates").storageKey() },
        binding = { scale, runtime -> scale?.let { bindSelectedCandidates(it, runtime) } }
    )

    suspend fun getDelegationScheduledRequests(chainId: ChainId, accountId: AccountId) = remoteStorage.query(
        chainId,
        keyBuilder = { runtime ->
            runtime.metadata.parachainStaking().storage("DelegationScheduledRequests").storageKey(runtime, accountId)
        },
        binding = { scale, runtime ->
            scale?.let { bindDelegationScheduledRequests(it, runtime) }
        }
    )

    suspend fun getScheduledRequests(chainId: ChainId, collatorIds: List<AccountId>) = remoteStorage.queryKeys(
        chainId,
        keysBuilder = { runtime ->
            val storage = runtime.metadata.parachainStaking().storage("DelegationScheduledRequests")
            storage.storageKeys(runtime, singleMapArguments = collatorIds, argumentTransform = { it.toHexString() })
        },
        binding = { scale, runtime ->
            scale?.let { bindDelegationScheduledRequests(it, runtime) }
        }
    )

    suspend fun getBottomDelegations(chainId: ChainId, addresses20: List<ByteArray>): AccountIdMap<List<Delegation>> {
        return remoteStorage.queryKeys(
            chainId = chainId,
            keysBuilder = { runtime ->
                val storage = runtime.metadata.parachainStaking().storage("BottomDelegations")
                storage.storageKeys(
                    runtime = runtime,
                    singleMapArguments = addresses20,
                    argumentTransform = { it.toHexString() }
                )
            },
            binding = { scale, runtime ->
                scale?.let { bindDelegations(it, runtime) } ?: incompatible()
            }
        )
    }
}

fun bindDelegations(scale: String, runtime: RuntimeSnapshot): List<Delegation> {
    val type = runtime.metadata.parachainStaking().storage("BottomDelegations").returnType()

    val dynamicInstance = type.fromHexOrNull(runtime, scale) ?: return emptyList()
    requireType<Struct.Instance>(dynamicInstance)

    return (dynamicInstance.getList("delegations")).map { it as Struct.Instance }
        .map { Delegation(it["owner"] ?: incompatible(), it["amount"] ?: incompatible()) }
}

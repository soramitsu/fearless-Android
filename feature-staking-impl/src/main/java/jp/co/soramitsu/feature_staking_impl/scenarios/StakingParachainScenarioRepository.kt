package jp.co.soramitsu.feature_staking_impl.scenarios

import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.utils.parachainStaking
import jp.co.soramitsu.common.utils.storageKeys
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.model.CandidateInfo
import jp.co.soramitsu.feature_staking_api.domain.model.DelegatorState
import jp.co.soramitsu.feature_staking_api.domain.model.Round
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.toDelegations
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindCandidateInfo
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindDelegatorState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindRound
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindSelectedCandidates
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.observeNonNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StakingParachainScenarioRepository(
    private val remoteStorage: StorageDataSource,
    private val localStorage: StorageDataSource,) {

    suspend fun stakingStateFlow(
        chain: Chain,
        accountId: AccountId
    ): Flow<StakingState> {
        return getDelegatorState(chain.id, accountId).map {
            when {
                it != null -> StakingState.Parachain.Delegator(chain, accountId, it.toDelegations(), it.total.toBigDecimal())
                else -> StakingState.Parachain.None(chain, accountId)
            }
        }
    }

    suspend fun getDelegatorState(chainId: ChainId, accountId: AccountId): Flow<DelegatorState?> {
        return localStorage.observe(
            chainId = chainId,
            keyBuilder = {
                it.metadata.parachainStaking().storage("DelegatorState").storageKey(it, accountId)
            },
            binder = { scale, runtime ->
                scale?.let { bindDelegatorState(it, runtime) }
            }
        )
    }

    suspend fun getCandidateInfos(chainId: ChainId, addresses20: List<ByteArray>): AccountIdMap<CandidateInfo?> {
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
                scale?.let { bindCandidateInfo(it, runtime) }
            }
        )
    }

    suspend fun getCurrentRound(chainId: ChainId): Round {
        return remoteStorage.query(chainId, keyBuilder = { runtime ->
            runtime.metadata.parachainStaking().storage("Round").storageKey()
        }, binding = { scale, runtime ->
            scale?.let { bindRound(it, runtime) } ?: incompatible()
        })
    }

    fun observeSelectedCandidates(chainId: ChainId) = remoteStorage.observeNonNull(
        chainId = chainId,
        keyBuilder = { it.metadata.parachainStaking().storage("SelectedCandidates").storageKey() },
        binding = { scale, runtime -> bindSelectedCandidates(scale, runtime) }
    )
}

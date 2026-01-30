package jp.co.soramitsu.staking.impl.domain.solana

import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.domain.isSolanaChainId
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.Base58Ext.toBase58
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

class SolanaStakingInteractor @Inject constructor(
    private val stakingSharedState: StakingSharedState,
    private val accountRepository: AccountRepository,
    private val solanaRpcService: SolanaRpcService
) {

    data class SolanaSnapshot(
        val chain: Chain,
        val publicKey: ByteArray,
        val address: String
    )

    data class SolanaNetworkSnapshot(
        val chain: Chain,
        val validators: List<SolanaValidator>
    )

    companion object {
        private const val FALLBACK_RPC = "https://api.mainnet-beta.solana.com"
    }

    fun snapshotFlow(): Flow<SolanaSnapshot> {
        return combine(
            stakingSharedState.assetWithChain,
            accountRepository.selectedMetaAccountFlow()
        ) { (chain, _), meta ->
            if (!isSolanaChainId(chain.id)) return@combine null
            val publicKey = meta.solanaPublicKey ?: return@combine null

            SolanaSnapshot(chain, publicKey, publicKey.toBase58())
        }.filterNotNull()
    }

    fun networkInfoFlow(): Flow<LoadingState<SolanaNetworkSnapshot>> {
        return snapshotFlow().flatMapLatest { snapshot ->
            flow {
                emit(LoadingState.Loading())
                val rpcUrl = snapshot.chain.nodes.firstOrNull { it.isDefault }?.url
                    ?: snapshot.chain.nodes.firstOrNull()?.url
                    ?: FALLBACK_RPC
                val validators = solanaRpcService.fetchValidators(rpcUrl)
                emit(LoadingState.Loaded(SolanaNetworkSnapshot(snapshot.chain, validators)))
            }.catch {
                emit(LoadingState.Loaded(SolanaNetworkSnapshot(snapshot.chain, emptyList())))
            }
        }
    }
}

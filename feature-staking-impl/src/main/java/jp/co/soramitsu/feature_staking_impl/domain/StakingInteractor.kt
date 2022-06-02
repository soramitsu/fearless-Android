package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.utils.combineToPair
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapAccountToStakingAccount
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import jp.co.soramitsu.runtime.state.chain
import jp.co.soramitsu.runtime.state.chainAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class StakingInteractor(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val stakingRewardsRepository: StakingRewardsRepository,
    private val stakingSharedState: StakingSharedState,
    private val assetUseCase: AssetUseCase,
    private val chainStateRepository: ChainStateRepository,
) {

    suspend fun syncStakingRewards(chainId: ChainId, accountAddress: String) = withContext(Dispatchers.IO) {
        runCatching {
            stakingRewardsRepository.sync(chainId, accountAddress)
        }
    }

    fun selectedChainFlow() = stakingSharedState.assetWithChain.map { it.chain }

    fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>> {
        return stakingRepository.stakingStoriesFlow()
    }

    fun selectionStateFlow() = combineToPair(
        accountRepository.selectedMetaAccountFlow(),
        stakingSharedState.assetWithChain
    )

    suspend fun getAccountProjectionsInSelectedChains() = withContext(Dispatchers.Default) {
        val chain = stakingSharedState.chain()

        accountRepository.allMetaAccounts().map {
            mapAccountToStakingAccount(chain, it)
        }
    }

    fun currentAssetFlow() = assetUseCase.currentAssetFlow()

    suspend fun getCurrentAsset(): Chain.Asset {
        return stakingSharedState.chainAsset()
    }

    fun assetFlow(accountAddress: String): Flow<Asset> {
        return flow {
            val (chain, chainAsset) = stakingSharedState.assetWithChain.first()
            val meta = accountRepository.getSelectedMetaAccount()

            emitAll(
                walletRepository.assetFlow(
                    metaId = meta.id,
                    accountId = chain.accountIdOf(accountAddress),
                    chainAsset = chainAsset,
                    minSupportedVersion = chain.minSupportedVersion
                )
            )
        }
    }

    fun selectedAccountProjectionFlow(): Flow<StakingAccount> {
        return combine(
            stakingSharedState.assetWithChain,
            accountRepository.selectedMetaAccountFlow()
        ) { (chain, _), account ->
            mapAccountToStakingAccount(chain, account)
        }
    }

    suspend fun getProjectedAccount(address: String): StakingAccount = withContext(Dispatchers.Default) {
        val chain = stakingSharedState.chain()
        val accountId = chain.accountIdOf(address)

        val metaAccount = accountRepository.findMetaAccount(accountId)!!

        mapAccountToStakingAccount(chain, metaAccount)
    }

    suspend fun getSelectedAccountProjection(): StakingAccount = withContext(Dispatchers.Default) {
        val chain = stakingSharedState.chain()
        val metaAccount = accountRepository.getSelectedMetaAccount()

        mapAccountToStakingAccount(chain, metaAccount)
    }

    suspend fun currentBlockNumber(): BlockNumber {
        return chainStateRepository.currentBlock(getSelectedChain().id)
    }
}

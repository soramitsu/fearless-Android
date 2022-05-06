package jp.co.soramitsu.feature_staking_impl.domain.scenarios

import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.utils.combineToPair
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.accountId
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios.StakingScenarioRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.runtime.state.chainAndAsset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

abstract class StakingScenarioInteractor(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val scenarioRepository: StakingScenarioRepository,
    private val stakingSharedState: StakingSharedState,
    private val stakingRepository: StakingRepository,
) {

    fun selectionStateFlow() = combineToPair(
        accountRepository.selectedMetaAccountFlow(),
        stakingSharedState.assetWithChain
    )

    fun selectedAccountStakingStateFlow(
        metaAccount: MetaAccount,
        assetWithChain: SingleAssetSharedState.AssetWithChain
    ) = flow {
        val (chain, chainAsset) = assetWithChain
        val accountId = metaAccount.accountId(chain)!! // TODO may be null for ethereum chains

        emitAll(scenarioRepository.stakingStateFlow(chain, chainAsset, accountId))
    }

    fun selectedAccountStakingStateFlow() = selectionStateFlow().flatMapLatest { (selectedAccount, assetWithChain) ->
        selectedAccountStakingStateFlow(selectedAccount, assetWithChain)
    }

    suspend fun observeNetworkInfoState(): Flow<NetworkInfo> {
        val chainId = stakingSharedState.chainId()
        return observeNetworkInfoState(chainId)
    }

    protected abstract suspend fun observeNetworkInfoState(chainId: ChainId): Flow<NetworkInfo>

    fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>> {

        return stakingRepository.stakingStoriesFlow()
    }

    suspend fun getCurrentAsset(): Flow<Asset> {
        val (chain, asset) = stakingSharedState.chainAndAsset()
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val accountId = metaAccount.accountId(chain)!!

        return walletRepository.assetFlow(
            metaId = metaAccount.id,
            accountId = accountId,
            chainAsset = asset,
            minSupportedVersion = chain.minSupportedVersion
        )
    }

    suspend fun getStakingStateFlow(): Flow<StakingState> {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        return stakingSharedState.assetWithChain.flatMapLatest {
            val accountId = metaAccount.accountId(it.chain) ?: error("Wrong account or chain")
            getStakingStateFlow(it.chain, it.asset, accountId)
        }
    }

    protected abstract suspend fun getStakingStateFlow(chain: Chain, chainAsset: Chain.Asset, accountId: AccountId): Flow<StakingState>
}

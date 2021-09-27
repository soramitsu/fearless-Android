package jp.co.soramitsu.feature_wallet_api.domain.implementations

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.accountIdIn
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

class AssetUseCaseImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val sharedState: SingleAssetSharedState
) : AssetUseCase {

    override fun currentAssetFlow() = combine(
        accountRepository.selectedMetaAccountFlow(),
        sharedState.assetWithChainWithChain,
        ::Pair
    ).flatMapLatest { (selectedMetaAccount, chainAndAsset) ->
        val (chain, chainAsset) = chainAndAsset

        walletRepository.assetFlow(
            accountId = selectedMetaAccount.accountIdIn(chain)!!,
            chainAsset = chainAsset
        )
    }

    override suspend fun availableAssetsToSelect(): List<Asset> = withContext(Dispatchers.Default) {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val availableChainAssets = sharedState.availableToSelect().toSet()

        walletRepository.getAssets(metaAccount.id).filter {
            it.token.configuration in availableChainAssets
        }
    }
}

package jp.co.soramitsu.wallet.impl.domain.implementations

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.wallet.api.domain.AssetUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

class AssetUseCaseImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val sharedState: SingleAssetSharedState
) : AssetUseCase {

    override fun currentAssetFlow() = sharedState.assetWithChain
        .map { chainAndAsset ->
            val meta = accountRepository.getSelectedMetaAccount()
            meta.accountId(chainAndAsset.chain)?.let {
                Pair(meta, chainAndAsset)
            }
        }.mapNotNull { it }
        .flatMapLatest { (selectedMetaAccount, chainAndAsset) ->
            val (chain, chainAsset) = chainAndAsset

            walletRepository.assetFlow(
                metaId = selectedMetaAccount.id,
                accountId = selectedMetaAccount.accountId(chain)!!,
                chainAsset = chainAsset,
                minSupportedVersion = chain.minSupportedVersion
            )
        }

    override suspend fun availableAssetsToSelect(): List<Asset> = withContext(Dispatchers.Default) {
        val metaAccount = accountRepository.getSelectedMetaAccount()
        val availableChainAssets = sharedState.availableToSelect().toSet()

        walletRepository.getAssets(metaAccount.id).filter {
            it.token.configuration in availableChainAssets
        }.sortedBy {
            it.token.configuration.orderInStaking
        }
    }
}

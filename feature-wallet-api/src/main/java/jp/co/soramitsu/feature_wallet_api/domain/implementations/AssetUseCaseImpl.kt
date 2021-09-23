package jp.co.soramitsu.feature_wallet_api.domain.implementations

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.accountIdIn
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.emitAll

class AssetUseCaseImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val sharedState: SingleAssetSharedState
) : AssetUseCase {

    override fun currentAssetFlow() = combineTransform(
        accountRepository.selectedMetaAccountFlow(),
        sharedState.selectedAsset
    ) { selectedMetaAccount, (chain, chainAsset) ->
        emitAll(
            walletRepository.assetFlow(
                accountId= selectedMetaAccount.accountIdIn(chain)!!,
                chainAsset = chainAsset
            )
        )
    }
}

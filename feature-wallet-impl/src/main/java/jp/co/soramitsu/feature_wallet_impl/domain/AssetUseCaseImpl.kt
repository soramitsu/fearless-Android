package jp.co.soramitsu.feature_wallet_impl.domain

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class AssetUseCaseImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository
) : AssetUseCase {

    override fun currentAssetFlow() = accountRepository.selectedAccountFlow()
        .flatMapLatest { assetFlow(it.address) }

    override fun assetFlow(accountAddress: String): Flow<Asset> {
        return walletRepository.assetsFlow(accountAddress)
            .filter { it.isNotEmpty() }
            .map { it.first() }
    }
}

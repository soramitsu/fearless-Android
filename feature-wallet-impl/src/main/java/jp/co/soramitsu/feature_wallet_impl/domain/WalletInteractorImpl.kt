package jp.co.soramitsu.feature_wallet_impl.domain

import io.reactivex.Observable
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset

class WalletInteractorImpl(
    private val walletRepository: WalletRepository
) : WalletInteractor {

    override fun getAssets(): Observable<List<Asset>> {
        return walletRepository.getAssets()
    }
}
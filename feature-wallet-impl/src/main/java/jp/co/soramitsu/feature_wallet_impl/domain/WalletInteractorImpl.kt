package jp.co.soramitsu.feature_wallet_impl.domain

import io.reactivex.Single
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset

class WalletInteractorImpl(
    val walletRepository: WalletRepository
) : WalletInteractor {

    override fun getAssets(): Single<List<Asset>> {
        return Single.fromCallable {
            listOf(
                Asset(
                    token = Asset.Token.KSM,
                    balance = 120.0849,
                    dollarRate = 5.28,
                    recentRateChange = 5.0
                )
            )
        }
    }
}
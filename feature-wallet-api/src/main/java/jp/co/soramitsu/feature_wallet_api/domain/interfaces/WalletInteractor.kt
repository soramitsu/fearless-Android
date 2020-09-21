package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Single
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset

interface WalletInteractor {
    fun getAssets() : Single<List<Asset>>
}
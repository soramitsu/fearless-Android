package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Observable
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset

interface WalletInteractor {
    fun getAssets(): Observable<List<Asset>>
}
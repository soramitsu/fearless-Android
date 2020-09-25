package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset

interface WalletInteractor {
    fun getAssets(): Observable<List<Asset>>

    fun syncAssets(): Completable

    fun observeSelectedAddressId() : Observable<ByteArray>
}
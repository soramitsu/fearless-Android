package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction

interface WalletInteractor {
    fun observeAssets(): Observable<List<Asset>>

    fun syncAssets(): Completable

    fun observeAsset(token: Asset.Token): Observable<Asset>

    fun syncAsset(token: Asset.Token): Completable

    fun observeTransactionsFirstPage(pageSize: Int): Observable<List<Transaction>>

    fun syncTransactionsFirstPage(pageSize: Int): Completable

    fun getTransactionPage(pageSize: Int, page: Int): Single<List<Transaction>>

    fun observeSelectedAddressId(): Observable<ByteArray>
}
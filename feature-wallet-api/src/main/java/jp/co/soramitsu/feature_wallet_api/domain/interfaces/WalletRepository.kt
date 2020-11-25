package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.CheckFundsStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import java.math.BigDecimal

interface WalletRepository {
    fun observeAssets(): Observable<List<Asset>>

    fun syncAssetsRates(): Completable

    fun observeAsset(token: Asset.Token): Observable<Asset>

    fun syncAsset(token: Asset.Token): Completable

    fun observeTransactionsFirstPage(pageSize: Int): Observable<List<Transaction>>

    fun syncTransactionsFirstPage(pageSize: Int): Completable

    fun getTransactionPage(pageSize: Int, page: Int): Single<List<Transaction>>

    fun getContacts(query: String): Single<List<String>>

    fun getTransferFee(transfer: Transfer): Single<Fee>

    fun performTransfer(transfer: Transfer, fee: BigDecimal): Completable

    fun checkEnoughAmountForTransfer(transfer: Transfer): Single<CheckFundsStatus>

    fun listenForUpdates(account: Account): Completable
}
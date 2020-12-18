package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import java.math.BigDecimal

interface WalletRepository {
    fun observeAssets(): Observable<List<Asset>>

    fun syncAssetsRates(): Completable

    fun observeAsset(type: Token.Type): Observable<Asset>

    fun syncAsset(type: Token.Type): Completable

    fun observeTransactionsFirstPage(pageSize: Int): Observable<List<Transaction>>

    fun syncTransactionsFirstPage(pageSize: Int): Completable

    fun getTransactionPage(pageSize: Int, page: Int): Single<List<Transaction>>

    fun getContacts(query: String): Single<Set<String>>

    fun getTransferFee(transfer: Transfer): Single<Fee>

    fun performTransfer(transfer: Transfer, fee: BigDecimal): Completable

    fun checkTransferValidity(transfer: Transfer): Single<TransferValidityStatus>

    fun listenForUpdates(account: Account): Completable
}
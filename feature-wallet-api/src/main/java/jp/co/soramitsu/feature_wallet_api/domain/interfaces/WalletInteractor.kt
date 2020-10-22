package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionsPage
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import java.math.BigDecimal

interface WalletInteractor {
    fun observeAssets(): Observable<List<Asset>>

    fun syncAssets(withoutRates: Boolean = false): Completable

    fun observeAsset(token: Asset.Token): Observable<Asset>

    fun syncAsset(token: Asset.Token, withoutRates: Boolean = false): Completable

    fun observeCurrentAsset(): Observable<Asset>

    fun observeTransactionsFirstPage(pageSize: Int): Observable<List<Transaction>>

    fun syncTransactionsFirstPage(pageSize: Int): Completable

    fun getTransactionPage(pageSize: Int, page: Int): Single<TransactionsPage>

    fun observeSelectedAccount(): Observable<Account>

    fun getAddressId(address: String): Single<ByteArray>

    fun getContacts(query: String): Single<List<String>>

    fun validateSendAddress(address: String): Single<Boolean>

    fun getTransferFee(transfer: Transfer): Single<Fee>

    fun performTransfer(transfer: Transfer, fee: BigDecimal): Completable

    fun checkEnoughAmountForTransfer(transfer: Transfer): Single<Boolean>

    fun getAccountsInCurrentNetwork(): Single<List<Account>>

    fun selectAccount(address: String) : Completable
}
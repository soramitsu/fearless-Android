package jp.co.soramitsu.feature_wallet_api.domain.interfaces

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.CheckFundsStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.RecipientSearchResult
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import java.io.File
import java.math.BigDecimal

class NotEnoughFundsException : Exception()

interface WalletInteractor {
    fun observeAssets(): Observable<List<Asset>>

    fun syncAssetsRates(): Completable

    fun observeAsset(token: Asset.Token): Observable<Asset>

    fun syncAssetRates(token: Asset.Token): Completable

    fun observeCurrentAsset(): Observable<Asset>

    fun observeTransactionsFirstPage(pageSize: Int): Observable<List<Transaction>>

    fun syncTransactionsFirstPage(pageSize: Int): Completable

    fun getTransactionPage(pageSize: Int, page: Int): Single<List<Transaction>>

    fun observeSelectedAccount(): Observable<Account>

    fun getAddressId(address: String): Single<ByteArray>

    fun getRecipients(query: String): Single<RecipientSearchResult>

    fun validateSendAddress(address: String): Single<Boolean>

    fun getTransferFee(transfer: Transfer): Single<Fee>

    fun performTransfer(transfer: Transfer, fee: BigDecimal): Completable

    fun checkEnoughAmountForTransfer(transfer: Transfer): Single<CheckFundsStatus>

    fun getAccountsInCurrentNetwork(): Single<List<Account>>

    fun selectAccount(address: String): Completable

    fun getQrCodeSharingString(): Single<String>

    fun createFileInTempStorageAndRetrieveAsset(fileName: String): Single<Pair<File, Asset>>

    fun getRecipientFromQrCodeContent(content: String): Single<String>
}
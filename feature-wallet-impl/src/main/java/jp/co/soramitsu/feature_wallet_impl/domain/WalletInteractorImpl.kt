package jp.co.soramitsu.feature_wallet_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.fearless_utils.encrypt.qr.QrSharing
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.NotValidTransferStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.RecipientSearchResult
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityStatus
import java.io.File
import java.math.BigDecimal

class WalletInteractorImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val fileProvider: FileProvider
) : WalletInteractor {

    companion object {
        private const val QR_SHARE_PREFIX = "substrate"
    }

    override fun observeAssets(): Observable<List<Asset>> {
        return walletRepository.observeAssets()
            .filter { it.isNotEmpty() }
    }

    override fun syncAssetsRates(): Completable {
        return walletRepository.syncAssetsRates()
    }

    override fun observeAsset(type: Token.Type): Observable<Asset> {
        return walletRepository.observeAsset(type)
    }

    override fun syncAssetRates(type: Token.Type): Completable {
        return walletRepository.syncAsset(type)
    }

    override fun observeCurrentAsset(): Observable<Asset> {
        return accountRepository.observeSelectedAccount()
            .map { Token.Type.fromNetworkType(it.network.type) }
            .switchMap(walletRepository::observeAsset)
    }

    override fun observeTransactionsFirstPage(pageSize: Int): Observable<List<Transaction>> {
        return walletRepository.observeTransactionsFirstPage(pageSize)
            .distinctUntilChanged { previous, new -> areTransactionPagesTheSame(previous, new) }
    }

    private fun areTransactionPagesTheSame(previous: List<Transaction>, new: List<Transaction>): Boolean {
        if (previous.size != new.size) return false

        return previous.zip(new).all { (previousElement, currentElement) -> previousElement == currentElement }
    }

    override fun syncTransactionsFirstPage(pageSize: Int): Completable {
        return walletRepository.syncTransactionsFirstPage(pageSize)
    }

    override fun getTransactionPage(pageSize: Int, page: Int): Single<List<Transaction>> {
        return walletRepository.getTransactionPage(pageSize, page)
    }

    override fun observeSelectedAccount(): Observable<Account> {
        return accountRepository.observeSelectedAccount()
    }

    override fun getAddressId(address: String): Single<ByteArray> {
        return accountRepository.getAddressId(address)
    }

    override fun getRecipients(query: String): Single<RecipientSearchResult> {
        return accountRepository.getSelectedAccount().flatMap { account ->
            walletRepository.getContacts(query).flatMap { contacts ->
                accountRepository.getMyAccounts(query, account.network.type).map { myAddresses ->
                    val contactsWithoutMyAddresses = contacts - myAddresses
                    val myAddressesWithoutCurrent = myAddresses - account.address

                    RecipientSearchResult(myAddressesWithoutCurrent.toList(), contactsWithoutMyAddresses.toList())
                }
            }
        }
    }

    override fun validateSendAddress(address: String): Single<Boolean> {
        return accountRepository.isInCurrentNetwork(address)
            .onErrorReturnItem(false)
    }

    override fun getTransferFee(transfer: Transfer): Single<Fee> {
        return walletRepository.getTransferFee(transfer)
    }

    override fun performTransfer(
        transfer: Transfer,
        fee: BigDecimal,
        maxAllowedLevel: TransferValidityLevel
    ): Completable {
        return walletRepository.checkTransferValidity(transfer)
            .flatMapCompletable {
                if (it.level > maxAllowedLevel) {
                    throw NotValidTransferStatus(it)
                } else {
                    walletRepository.performTransfer(transfer, fee)
                }
            }
    }

    override fun checkEnoughAmountForTransfer(transfer: Transfer): Single<TransferValidityStatus> {
        return walletRepository.checkTransferValidity(transfer)
    }

    override fun getAccountsInCurrentNetwork(): Single<List<Account>> {
        return accountRepository.observeSelectedAccount().firstOrError()
            .flatMap {
                accountRepository.getAccountsByNetworkType(it.network.type)
            }
    }

    override fun selectAccount(address: String): Completable {
        return accountRepository.getAccount(address)
            .flatMapCompletable(accountRepository::selectAccount)
    }

    override fun getQrCodeSharingString(): Single<String> {
        return accountRepository.observeSelectedAccount()
            .firstOrError()
            .map(::formatQrAccountData)
    }

    private fun formatQrAccountData(account: Account): String {
        return with(account) {
            if (name.isNullOrEmpty()) {
                "$QR_SHARE_PREFIX:$address:$publicKey"
            } else {
                "$QR_SHARE_PREFIX:$address:$publicKey:$name"
            }
        }
    }

    override fun createFileInTempStorageAndRetrieveAsset(fileName: String): Single<Pair<File, Asset>> {
        return fileProvider.createFileInTempStorage(fileName)
            .flatMap { file ->
                observeCurrentAsset()
                    .firstOrError()
                    .map { Pair(file, it) }
            }
    }

    override fun getRecipientFromQrCodeContent(content: String): Single<String> {
        return Single.fromCallable { QrSharing.decode(content) }
            .map { it.address }
    }
}
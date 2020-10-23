package jp.co.soramitsu.feature_wallet_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionsPage
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import java.math.BigDecimal

class WalletInteractorImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository
) : WalletInteractor {

    override fun observeAssets(): Observable<List<Asset>> {
        return walletRepository.observeAssets()
    }

    override fun syncAssets(withoutRates: Boolean): Completable {
        return walletRepository.syncAssets(withoutRates)
    }

    override fun observeAsset(token: Asset.Token): Observable<Asset> {
        return walletRepository.observeAsset(token)
    }

    override fun syncAsset(token: Asset.Token, withoutRates: Boolean): Completable {
        return walletRepository.syncAsset(token, withoutRates)
    }

    override fun observeCurrentAsset(): Observable<Asset> {
        return accountRepository.observeSelectedAccount()
            .map { Asset.Token.fromNetworkType(it.network.type) }
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

    override fun observeSelectedAddressId(): Observable<ByteArray> {
        return accountRepository.observeSelectedAccount()
            .flatMapSingle { accountRepository.getAddressId(it) }
    }

    override fun getAddressId(address: String): Single<ByteArray> {
        return accountRepository.getAddressId(address)
    }

    override fun getContacts(query: String): Single<List<String>> {
        return accountRepository.observeSelectedAccount().firstOrError()
            .flatMap { walletRepository.getContacts(query, it.network.type) }
    }

    override fun validateSendAddress(address: String): Single<Boolean> {
        return getAddressId(address)
            .map { true }
            .onErrorReturn { false }
    }

    override fun getTransferFee(transfer: Transfer): Single<Fee> {
        return walletRepository.getTransferFee(transfer)
    }

    override fun performTransfer(transfer: Transfer, fee: BigDecimal): Completable {
        return walletRepository.performTransfer(transfer, fee)
    }

    override fun checkEnoughAmountForTransfer(transfer: Transfer): Single<Boolean> {
        return walletRepository.checkEnoughAmountForTransfer(transfer)
    }
}
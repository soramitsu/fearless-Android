package jp.co.soramitsu.feature_wallet_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction

class WalletInteractorImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository
) : WalletInteractor {

    override fun getAssets(): Observable<List<Asset>> {
        return walletRepository.getAssets()
    }

    override fun syncAssets(): Completable {
        return walletRepository.syncAssets()
    }

    override fun observeTransactionsFirstPage(pageSize: Int): Observable<List<Transaction>> {
        return walletRepository.observeTransactionsFirstPage(pageSize)
            .distinctUntilChanged { previous, new -> areTransactionPagesTheSame(previous, new) }
    }

    private fun areTransactionPagesTheSame(previous: List<Transaction>, new: List<Transaction>): Boolean {
        if (previous.size != new.size) return false

        return previous.zip(new).all { (previousElement, currentElement) -> previousElement.hash == currentElement.hash }
    }

    override fun syncTransactionsFirstPage(pageSize: Int): Completable {
        return walletRepository.syncTransactionsFirstPage(pageSize)
    }

    override fun getTransactionPage(pageSize: Int, page: Int): Single<List<Transaction>> {
        return walletRepository.getTransactionPage(pageSize, page)
    }

    override fun observeSelectedAddressId(): Observable<ByteArray> {
        return accountRepository.observeSelectedAccount()
            .map { accountRepository.getAddressId(it).blockingGet() }
    }
}
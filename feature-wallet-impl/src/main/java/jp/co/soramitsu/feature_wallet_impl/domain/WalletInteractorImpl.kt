package jp.co.soramitsu.feature_wallet_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset

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

    override fun observeSelectedAddressId(): Observable<ByteArray> {
        return accountRepository.observeSelectedAccount()
            .map { accountRepository.getAddressId(it).blockingGet() }
    }
}
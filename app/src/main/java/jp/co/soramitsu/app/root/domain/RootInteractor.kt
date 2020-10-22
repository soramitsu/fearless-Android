package jp.co.soramitsu.app.root.domain

import io.reactivex.Completable
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

class RootInteractor(
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository
) {
    fun observeSelectedNode() = accountRepository.observeSelectedNode()

    fun listenForAccountUpdates(networkType: Node.NetworkType): Completable = accountRepository.observeSelectedAccount()
        .filter { it.network.type == networkType }
        .distinctUntilChanged { old, new -> old.address == new.address }
        .switchMapCompletable(walletRepository::listenForUpdates)
}
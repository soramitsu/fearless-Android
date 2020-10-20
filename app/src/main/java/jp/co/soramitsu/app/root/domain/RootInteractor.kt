package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

class RootInteractor(
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository
) {
    fun observeSelectedNode() = accountRepository.observeSelectedNode()

    fun listenForAccountUpdates() = accountRepository.observeSelectedAccount()
        .firstOrError()
        .flatMapCompletable(walletRepository::listenForUpdates)
}
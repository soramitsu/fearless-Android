package jp.co.soramitsu.app.root.domain

import io.reactivex.Completable
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.ExternalProvider

class RootInteractor(
    private val accountRepository: AccountRepository,
    private val buyTokenRegistry: BuyTokenRegistry,
    private val walletRepository: WalletRepository
) {
    fun observeSelectedNode() = accountRepository.observeSelectedNode()

    fun listenForAccountUpdates(networkType: Node.NetworkType): Completable = accountRepository.observeSelectedAccount()
        .filter { it.network.type == networkType }
        .distinctUntilChanged { old, new -> old.address == new.address }
        .switchMapCompletable(walletRepository::listenForUpdates)

    fun isBuyProviderRedirectLink(link: String) = buyTokenRegistry.availableProviders
        .filterIsInstance<ExternalProvider>()
        .any { it.redirectLink == link }
}
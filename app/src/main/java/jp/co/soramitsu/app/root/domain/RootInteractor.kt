package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.ExternalProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.transformLatest

class RootInteractor(
    private val accountRepository: AccountRepository,
    private val buyTokenRegistry: BuyTokenRegistry,
    private val walletRepository: WalletRepository
) {
    fun selectedNodeFlow() = accountRepository.selectedNodeFlow()

    suspend fun listenForAccountUpdates(networkType: Node.NetworkType) = accountRepository.selectedAccountFlow()
        .filter { it.network.type == networkType }
        .distinctUntilChanged { old, new -> old.address == new.address }
        .collectLatest { walletRepository.listenForUpdates(it) }

    fun isBuyProviderRedirectLink(link: String) = buyTokenRegistry.availableProviders
        .filterIsInstance<ExternalProvider>()
        .any { it.redirectLink == link }
}
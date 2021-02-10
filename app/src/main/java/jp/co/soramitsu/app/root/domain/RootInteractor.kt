package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.core_api.data.network.Updater
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.ExternalProvider

class RootInteractor(
    private val accountRepository: AccountRepository,
    private val rootUpdater: Updater,
    private val buyTokenRegistry: BuyTokenRegistry
) {

    fun selectedNodeFlow() = accountRepository.selectedNodeFlow()

    suspend fun listenForUpdates() {
        rootUpdater.listenForUpdates()
    }

    fun isBuyProviderRedirectLink(link: String) = buyTokenRegistry.availableProviders
        .filterIsInstance<ExternalProvider>()
        .any { it.redirectLink == link }
}
package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.ExternalProvider
import kotlinx.coroutines.flow.Flow

class RootInteractor(
    private val accountRepository: AccountRepository,
    private val rootUpdater: RootUpdater,
    private val buyTokenRegistry: BuyTokenRegistry,
    private val walletRepository: WalletRepository
) {

    fun selectedNodeFlow() = accountRepository.selectedNodeFlow()

    suspend fun listenForUpdates(): Flow<Updater.SideEffect> {
        return rootUpdater.listenForUpdates()
    }

    fun isBuyProviderRedirectLink(link: String) = buyTokenRegistry.availableProviders
        .filterIsInstance<ExternalProvider>()
        .any { it.redirectLink == link }

    suspend fun updatePhishingAddresses() {
        runCatching {
            walletRepository.updatePhishingAddresses()
        }
    }
}

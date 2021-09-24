package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.ExternalProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class RootInteractor(
    private val updateSystem: UpdateSystem,
    private val walletRepository: WalletRepository,
) {

    fun runBalancesUpdate(): Flow<Updater.SideEffect> = updateSystem.start()

    fun isBuyProviderRedirectLink(link: String) = ExternalProvider.REDIRECT_URL_BASE in link

    fun stakingAvailableFlow() = flowOf(true) // TODO remove this logic

    suspend fun updatePhishingAddresses() {
        runCatching {
            walletRepository.updatePhishingAddresses()
        }
    }
}

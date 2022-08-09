package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.model.toDomain
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.featurewalletapi.domain.interfaces.WalletRepository
import jp.co.soramitsu.featurewalletimpl.data.buyToken.ExternalProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class RootInteractor(
    private val updateSystem: UpdateSystem,
    private val walletRepository: WalletRepository,
    private val preferences: Preferences
) {

    fun runBalancesUpdate(): Flow<Updater.SideEffect> = updateSystem.start()

    fun isBuyProviderRedirectLink(link: String) = ExternalProvider.REDIRECT_URL_BASE in link

    fun stakingAvailableFlow() = flowOf(true) // TODO remove this logic

    suspend fun updatePhishingAddresses() {
        runCatching {
            walletRepository.updatePhishingAddresses()
        }
    }

    suspend fun getRemoteConfig() = walletRepository.getRemoteConfig().map { it.toDomain() }

    fun chainRegistrySyncUp() = walletRepository.chainRegistrySyncUp()
}

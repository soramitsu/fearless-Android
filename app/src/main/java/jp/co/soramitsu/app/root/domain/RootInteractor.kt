package jp.co.soramitsu.app.root.domain

import com.walletconnect.web3.wallet.client.Web3Wallet
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.appConfig
import jp.co.soramitsu.common.domain.model.AppConfig
import jp.co.soramitsu.common.domain.model.toDomain
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.wallet.impl.data.buyToken.ExternalProvider
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

class RootInteractor(
    private val updateSystem: UpdateSystem,
    private val walletRepository: WalletRepository,
    private val pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario,
    private val preferences: Preferences
) {

    fun runBalancesUpdate(): Flow<Updater.SideEffect> = updateSystem.start().inBackground()

    fun isBuyProviderRedirectLink(link: String) = ExternalProvider.REDIRECT_URL_BASE in link

    fun stakingAvailableFlow() = flowOf(true) // TODO remove this logic

    suspend fun updatePhishingAddresses() {
        runCatching {
            walletRepository.updatePhishingAddresses()
        }
    }

    suspend fun getRemoteConfig(): Result<AppConfig> {
        return withContext(Dispatchers.Default) {
            val remoteVersion = walletRepository.getRemoteConfig()

            if (remoteVersion.isSuccess) {
                preferences.appConfig = remoteVersion.requireValue()
                remoteVersion
            } else {
                val localVersion = preferences.appConfig
                Result.success(localVersion)
            }.map { it.toDomain() }
        }
    }

    fun chainRegistrySyncUp() = walletRepository.chainRegistrySyncUp()

    suspend fun fetchFeatureToggle() = withContext(Dispatchers.Default) { pendulumPreInstalledAccountsScenario.fetchFeatureToggle() }

    suspend fun getPendingListOfSessionRequests(topic: String) = withContext(Dispatchers.Default){ Web3Wallet.getPendingListOfSessionRequests(topic) }
}

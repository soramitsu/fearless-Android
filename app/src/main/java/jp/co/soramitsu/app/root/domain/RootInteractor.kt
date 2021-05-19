package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.ExternalProvider
import kotlinx.coroutines.flow.Flow

class RootInteractor(
    private val accountRepository: AccountRepository,
    private val updateSystem: UpdateSystem,
    private val stakingRepository: StakingRepository,
    private val walletRepository: WalletRepository,
) {

    fun selectedNodeFlow() = accountRepository.selectedNodeFlow()

    suspend fun listenForUpdates(): Flow<Updater.SideEffect> {
        return updateSystem.start()
    }

    fun isBuyProviderRedirectLink(link: String) = ExternalProvider.REDIRECT_URL_BASE in link

    fun stakingAvailableFlow() = stakingRepository.stakingAvailableFlow()

    suspend fun updatePhishingAddresses() {
        runCatching {
            walletRepository.updatePhishingAddresses()
        }
    }
}

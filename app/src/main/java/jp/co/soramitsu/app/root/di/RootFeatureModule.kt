package jp.co.soramitsu.app.root.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.impl.domain.WalletSyncService
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository

@InstallIn(SingletonComponent::class)
@Module
class RootFeatureModule {

    @Provides
    fun provideRootInteractor(
        walletRepository: WalletRepository,
        @Named("BalancesUpdateSystem") walletUpdateSystem: UpdateSystem,
        pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario,
        preferences: Preferences,
        accountRepository: AccountRepository,
        walletSyncService: WalletSyncService
    ): RootInteractor {
        return RootInteractor(
            walletUpdateSystem,
            walletRepository,
            pendulumPreInstalledAccountsScenario,
            preferences,
            accountRepository,
            walletSyncService
        )
    }
}

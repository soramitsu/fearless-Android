package jp.co.soramitsu.app.root.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.impl.domain.WalletSyncService
import jp.co.soramitsu.app.root.domain.AppInitializer
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.RemoteAssetsInitializer
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.wallet.impl.data.repository.ChainlinkPricesService
import jp.co.soramitsu.wallet.impl.data.repository.PricesSyncService
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import javax.inject.Singleton

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
        walletSyncService: WalletSyncService,
        chainRegistry: ChainRegistry
    ): RootInteractor {
        return RootInteractor(
            walletUpdateSystem,
            walletRepository,
            pendulumPreInstalledAccountsScenario,
            preferences,
            accountRepository,
            walletSyncService,
            chainRegistry
        )
    }

    @Provides
    @Singleton
    fun provideAppInitializer(
        chainRegistry: ChainRegistry,
        chainSyncService: ChainSyncService,
        runtimeSyncService: RuntimeSyncService,
        pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario,
        walletSyncService: WalletSyncService,
        @Named("BalancesUpdateSystem") walletUpdateSystem: UpdateSystem,
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        remoteAssetsInitializer: RemoteAssetsInitializer,
        preferences: Preferences,
        getAvailableFiatCurrencies: GetAvailableFiatCurrencies,
        pricesService: PricesSyncService
    ): AppInitializer {
        return AppInitializer(
            chainRegistry,
            chainSyncService,
            runtimeSyncService,
            pendulumPreInstalledAccountsScenario,
            walletSyncService,
            walletUpdateSystem,
            walletRepository,
            accountRepository,
            remoteAssetsInitializer,
            preferences,
            getAvailableFiatCurrencies,
            pricesService
        )
    }
}

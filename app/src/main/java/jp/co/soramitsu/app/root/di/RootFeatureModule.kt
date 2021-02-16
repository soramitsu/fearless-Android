package jp.co.soramitsu.app.root.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.root.domain.CompositeUpdater
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_wallet_api.di.WalletUpdaters
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.runtime.RuntimeUpdater

@Module
class RootFeatureModule {

    @Provides
    @FeatureScope
    fun provideRootUpdater(
        walletUpdaters: WalletUpdaters,
        runtimeUpdater: RuntimeUpdater
    ): Updater {
        return CompositeUpdater(
            *walletUpdaters.updaters,
            runtimeUpdater
        )
    }

    @Provides
    @FeatureScope
    fun provideRootInteractor(
        accountRepository: AccountRepository,
        rootUpdater: Updater,
        buyTokenRegistry: BuyTokenRegistry,
        walletRepository: WalletRepository
    ): RootInteractor {
        return RootInteractor(accountRepository, rootUpdater, buyTokenRegistry, walletRepository)
    }
}
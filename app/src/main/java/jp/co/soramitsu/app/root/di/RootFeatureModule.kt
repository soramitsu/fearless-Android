package jp.co.soramitsu.app.root.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import javax.inject.Named

@InstallIn(SingletonComponent::class)
@Module
class RootFeatureModule {

    @Provides
    fun provideRootInteractor(
        walletRepository: WalletRepository,
        @Named("BalancesUpdateSystem") walletUpdateSystem: UpdateSystem,
        preferences: Preferences
    ): RootInteractor {
        return RootInteractor(
            walletUpdateSystem,
            walletRepository,
            preferences
        )
    }
}

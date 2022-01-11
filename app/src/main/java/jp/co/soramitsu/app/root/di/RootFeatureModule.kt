package jp.co.soramitsu.app.root.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.feature_wallet_api.di.Wallet
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

@Module
class RootFeatureModule {

    @Provides
    @FeatureScope
    fun provideRootInteractor(
        walletRepository: WalletRepository,
        @Wallet walletUpdateSystem: UpdateSystem,
    ): RootInteractor {
        return RootInteractor(
            walletUpdateSystem,
            walletRepository
        )
    }
}

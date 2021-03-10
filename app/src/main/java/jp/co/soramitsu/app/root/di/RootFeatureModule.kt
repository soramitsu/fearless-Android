package jp.co.soramitsu.app.root.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.app.root.domain.RootUpdater
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.di.StakingUpdaters
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
        stakingUpdaters: StakingUpdaters,
        runtimeUpdater: RuntimeUpdater,
        socketService: SocketService
    ): RootUpdater {
        return RootUpdater(
            runtimeUpdater,
            updaters = listOf(
                *walletUpdaters.updaters,
                *stakingUpdaters.globalUpdaters,
                runtimeUpdater
            ),
            socketService
        )
    }

    @Provides
    @FeatureScope
    fun provideRootInteractor(
        accountRepository: AccountRepository,
        rootUpdater: RootUpdater,
        buyTokenRegistry: BuyTokenRegistry,
        walletRepository: WalletRepository
    ): RootInteractor {
        return RootInteractor(accountRepository, rootUpdater, buyTokenRegistry, walletRepository)
    }
}
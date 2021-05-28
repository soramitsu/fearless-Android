package jp.co.soramitsu.app.root.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.app.root.domain.UpdateSystem
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.updaters.CrowdloanUpdaters
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_staking_api.di.StakingUpdaters
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_wallet_api.di.WalletUpdaters
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.runtime.RuntimeUpdater

@Module
class RootFeatureModule {

    @Provides
    @FeatureScope
    fun provideUpdateSystem(
        walletUpdaters: WalletUpdaters,
        stakingUpdaters: StakingUpdaters,
        crowdloanUpdaters: CrowdloanUpdaters,
        runtimeUpdater: RuntimeUpdater,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        socketService: SocketService
    ): UpdateSystem {
        return UpdateSystem(
            runtimeUpdater,
            runtimeProperty,
            updaters = listOf(
                *walletUpdaters.updaters,
                *stakingUpdaters.updaters,
                *crowdloanUpdaters.updaters,
                runtimeUpdater
            ),
            socketService
        )
    }

    @Provides
    @FeatureScope
    fun provideRootInteractor(
        accountRepository: AccountRepository,
        stakingRepository: StakingRepository,
        crowdloanRepository: CrowdloanRepository,
        updateSystem: UpdateSystem,
        walletRepository: WalletRepository
    ): RootInteractor {
        return RootInteractor(
            accountRepository,
            updateSystem,
            stakingRepository,
            crowdloanRepository,
            walletRepository
        )
    }
}

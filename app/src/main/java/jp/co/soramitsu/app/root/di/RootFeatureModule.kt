package jp.co.soramitsu.app.root.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.app.root.domain.RootInteractor
import jp.co.soramitsu.app.root.domain.UpdateSystem
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.updaters.CrowdloanUpdaters
import jp.co.soramitsu.feature_staking_api.di.StakingUpdaters
import jp.co.soramitsu.feature_wallet_api.di.WalletUpdaters
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@Module
class RootFeatureModule {

    @Provides
    @FeatureScope
    fun provideUpdateSystem(
        walletUpdaters: WalletUpdaters,
        stakingUpdaters: StakingUpdaters,
        crowdloanUpdaters: CrowdloanUpdaters,
        chainRegistry: ChainRegistry,
    ): UpdateSystem {
        return UpdateSystem(
            updaters = listOf(
                *walletUpdaters.updaters,
                *stakingUpdaters.updaters,
                *crowdloanUpdaters.updaters
            ),
            chainRegistry
        )
    }

    @Provides
    @FeatureScope
    fun provideRootInteractor(
        updateSystem: UpdateSystem,
        walletRepository: WalletRepository
    ): RootInteractor {
        return RootInteractor(
            updateSystem,
            walletRepository
        )
    }
}

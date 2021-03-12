package jp.co.soramitsu.app.root.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.app.root.presentation.di.RootActivityComponent
import jp.co.soramitsu.app.root.presentation.main.coming_soon.di.ComingSoonComponent
import jp.co.soramitsu.app.root.presentation.main.di.MainFragmentComponent
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.runtime.di.RuntimeApi

@Component(
    dependencies = [
        RootDependencies::class
    ],
    modules = [
        RootFeatureModule::class
    ]
)
@FeatureScope
interface RootComponent {

    fun mainActivityComponentFactory(): RootActivityComponent.Factory

    fun mainFragmentComponentFactory(): MainFragmentComponent.Factory

    fun comingSoonComponentFactory(): ComingSoonComponent.Factory

    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance navigator: Navigator,
            deps: RootDependencies
        ): RootComponent
    }

    @Component(
        dependencies = [
            AccountFeatureApi::class,
            WalletFeatureApi::class,
            StakingFeatureApi::class,
            DbApi::class,
            CommonApi::class,
            RuntimeApi::class
        ]
    )
    interface RootFeatureDependenciesComponent : RootDependencies
}

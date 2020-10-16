package jp.co.soramitsu.app.root.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.app.root.presentation.di.RootActivityComponent
import jp.co.soramitsu.app.root.presentation.main.di.MainFragmentComponent
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi

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
            CommonApi::class
        ]
    )
    interface RootFeatureDependenciesComponent : RootDependencies
}
package jp.co.soramitsu.feature_account_impl.di

import dagger.Component
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi

@FeatureScope
@Component(
    dependencies = [
        AccountFeatureDependencies::class
    ],
    modules = [
        AccountFeatureModule::class
    ]
)
interface AccountFeatureComponent : AccountFeatureApi {

    @Component(
        dependencies = [
            CommonApi::class
        ]
    )
    interface AccountFeatureDependenciesComponent : AccountFeatureDependencies
}
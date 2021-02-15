package jp.co.soramitsu.runtime.di

import dagger.Component
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi

@Component(
    modules = [
        RuntimeModule::class
    ],
    dependencies = [
        RuntimeDependencies::class
    ]
)
@ApplicationScope
abstract class RuntimeComponent : RuntimeApi {

    @Component(
        dependencies = [
            CommonApi::class,
            DbApi::class,
            AccountFeatureApi::class
        ]
    )
    interface RuntimeDependenciesComponent : RuntimeDependencies
}
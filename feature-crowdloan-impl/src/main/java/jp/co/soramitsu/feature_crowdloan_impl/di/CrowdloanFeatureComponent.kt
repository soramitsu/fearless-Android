package jp.co.soramitsu.feature_crowdloan_impl.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.main.di.CrowdloanComponent
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.runtime.di.RuntimeApi

@Component(
    dependencies = [
        CrowdloanFeatureDependencies::class
    ],
    modules = [
        CrowdloanFeatureModule::class
    ]
)
@FeatureScope
interface CrowdloanFeatureComponent : CrowdloanFeatureApi {

    fun crowdloansFactory(): CrowdloanComponent.Factory

    @Component.Factory
    interface Factory {

        fun create(
            @BindsInstance router: CrowdloanRouter,
            deps: CrowdloanFeatureDependencies
        ): CrowdloanFeatureComponent
    }

    @Component(
        dependencies = [
            CommonApi::class,
            DbApi::class,
            RuntimeApi::class,
            AccountFeatureApi::class,
            WalletFeatureApi::class
        ]
    )
    interface CrowdloanFeatureDependenciesComponent : CrowdloanFeatureDependencies
}

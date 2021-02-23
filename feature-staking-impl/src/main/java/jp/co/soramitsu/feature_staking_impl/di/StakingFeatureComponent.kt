package jp.co.soramitsu.feature_staking_impl.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended.di.RecommendedValidatorsComponent
import jp.co.soramitsu.runtime.di.RuntimeApi

@Component(
    dependencies = [
        StakingFeatureDependencies::class
    ],
    modules = [
        StakingFeatureModule::class,
        StakingUpdatersModule::class
    ]
)
@FeatureScope
interface StakingFeatureComponent : StakingFeatureApi {

    fun recommendedValidatorsComponentFactory(): RecommendedValidatorsComponent.Factory

    @Component.Factory
    interface Factory {

        fun create(
            @BindsInstance router: StakingRouter,
            deps: StakingFeatureDependencies
        ): StakingFeatureComponent
    }

    @Component(
        dependencies = [
            CommonApi::class,
            DbApi::class,
            RuntimeApi::class,
            AccountFeatureApi::class
        ]
    )
    interface StakingFeatureDependenciesComponent : StakingFeatureDependencies
}
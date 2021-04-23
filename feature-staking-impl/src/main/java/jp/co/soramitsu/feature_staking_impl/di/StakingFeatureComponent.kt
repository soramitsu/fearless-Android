package jp.co.soramitsu.feature_staking_impl.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.confirm.di.ConfirmStakingComponent
import jp.co.soramitsu.feature_staking_impl.presentation.confirm.nominations.di.ConfirmNominationsComponent
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.di.ConfirmPayoutComponent
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.detail.di.PayoutDetailsComponent
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.di.PayoutsListComponent
import jp.co.soramitsu.feature_staking_impl.presentation.setup.di.SetupStakingComponent
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.di.StakingBalanceComponent
import jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select.di.SelectBondMoreComponent
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.di.StakingComponent
import jp.co.soramitsu.feature_staking_impl.presentation.story.di.StoryComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.di.ValidatorDetailsComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended.di.RecommendedValidatorsComponent
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.runtime.di.RuntimeApi

@Component(
    dependencies = [
        StakingFeatureDependencies::class
    ],
    modules = [
        StakingFeatureModule::class,
        StakingUpdatersModule::class,
        StakingValidationModule::class
    ]
)
@FeatureScope
interface StakingFeatureComponent : StakingFeatureApi {

    fun recommendedValidatorsComponentFactory(): RecommendedValidatorsComponent.Factory

    fun stakingComponentFactory(): StakingComponent.Factory

    fun setupStakingComponentFactory(): SetupStakingComponent.Factory

    fun confirmStakingComponentFactory(): ConfirmStakingComponent.Factory

    fun confirmNominationsComponentFactory(): ConfirmNominationsComponent.Factory

    fun validatorDetailsComponentFactory(): ValidatorDetailsComponent.Factory

    fun storyComponentFactory(): StoryComponent.Factory

    fun payoutsListFactory(): PayoutsListComponent.Factory

    fun payoutDetailsFactory(): PayoutDetailsComponent.Factory

    fun confirmPayoutFactory(): ConfirmPayoutComponent.Factory

    fun stakingBalanceFactory(): StakingBalanceComponent.Factory

    fun selectBondMoreFactory(): SelectBondMoreComponent.Factory

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
            AccountFeatureApi::class,
            WalletFeatureApi::class
        ]
    )
    interface StakingFeatureDependenciesComponent : StakingFeatureDependencies
}

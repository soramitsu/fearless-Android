package jp.co.soramitsu.feature_staking_impl.di

import dagger.Module
import jp.co.soramitsu.feature_staking_impl.di.validations.BondMoreValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.MakePayoutValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.RebondValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.RedeemValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.RewardDestinationValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.SetControllerValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.SetupStakingValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.StakingBalanceValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.UnbondValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.WelcomeStakingValidationModule

@Module(
    includes = [
        MakePayoutValidationsModule::class,
        SetupStakingValidationsModule::class,
        StakingBalanceValidationsModule::class,
        BondMoreValidationsModule::class,
        UnbondValidationsModule::class,
        RedeemValidationsModule::class,
        RebondValidationsModule::class,
        SetControllerValidationsModule::class,
        RewardDestinationValidationsModule::class,
        WelcomeStakingValidationModule::class
    ]
)
class StakingValidationModule

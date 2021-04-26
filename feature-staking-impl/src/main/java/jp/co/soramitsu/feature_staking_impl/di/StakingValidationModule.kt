package jp.co.soramitsu.feature_staking_impl.di

import dagger.Module
import jp.co.soramitsu.feature_staking_impl.di.validations.BondMoreValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.MakePayoutValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.SetupStakingValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.StakingBalanceValidationsModule

@Module(
    includes = [
        MakePayoutValidationsModule::class,
        SetupStakingValidationsModule::class,
        StakingBalanceValidationsModule::class,
        BondMoreValidationsModule::class
    ]
)
class StakingValidationModule

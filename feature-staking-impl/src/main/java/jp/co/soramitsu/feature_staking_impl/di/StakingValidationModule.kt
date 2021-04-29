package jp.co.soramitsu.feature_staking_impl.di

import dagger.Module
import jp.co.soramitsu.feature_staking_impl.di.validations.BondMoreValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.MakePayoutValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.RedeemValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.SetupStakingValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.StakingBalanceValidationsModule
import jp.co.soramitsu.feature_staking_impl.di.validations.UnbondValidationsModule

@Module(
    includes = [
        MakePayoutValidationsModule::class,
        SetupStakingValidationsModule::class,
        StakingBalanceValidationsModule::class,
        BondMoreValidationsModule::class,
        UnbondValidationsModule::class,
        RedeemValidationsModule::class,
    ]
)
class StakingValidationModule

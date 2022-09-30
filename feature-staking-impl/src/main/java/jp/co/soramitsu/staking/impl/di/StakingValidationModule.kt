package jp.co.soramitsu.staking.impl.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.staking.impl.di.validations.BondMoreValidationsModule
import jp.co.soramitsu.staking.impl.di.validations.MakePayoutValidationsModule
import jp.co.soramitsu.staking.impl.di.validations.RebondValidationsModule
import jp.co.soramitsu.staking.impl.di.validations.RedeemValidationsModule
import jp.co.soramitsu.staking.impl.di.validations.RewardDestinationValidationsModule
import jp.co.soramitsu.staking.impl.di.validations.SetControllerValidationsModule
import jp.co.soramitsu.staking.impl.di.validations.UnbondValidationsModule

@InstallIn(SingletonComponent::class)
@Module(
    includes = [
        MakePayoutValidationsModule::class,
        BondMoreValidationsModule::class,
        UnbondValidationsModule::class,
        RedeemValidationsModule::class,
        RebondValidationsModule::class,
        SetControllerValidationsModule::class,
        RewardDestinationValidationsModule::class
    ]
)
class StakingValidationModule

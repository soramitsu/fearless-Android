package jp.co.soramitsu.featurestakingimpl.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.featurestakingimpl.di.validations.BondMoreValidationsModule
import jp.co.soramitsu.featurestakingimpl.di.validations.MakePayoutValidationsModule
import jp.co.soramitsu.featurestakingimpl.di.validations.RebondValidationsModule
import jp.co.soramitsu.featurestakingimpl.di.validations.RedeemValidationsModule
import jp.co.soramitsu.featurestakingimpl.di.validations.RewardDestinationValidationsModule
import jp.co.soramitsu.featurestakingimpl.di.validations.SetControllerValidationsModule
import jp.co.soramitsu.featurestakingimpl.di.validations.UnbondValidationsModule

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

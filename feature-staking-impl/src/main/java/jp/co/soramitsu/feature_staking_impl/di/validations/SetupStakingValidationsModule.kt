package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.domain.validations.MaxNominatorsReachedValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.MinimumAmountValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingElectionValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingMaximumNominatorsValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation
import jp.co.soramitsu.feature_wallet_api.domain.validation.assetBalanceProducer
import java.math.BigDecimal

@Module
class SetupStakingValidationsModule {

    @Provides
    @FeatureScope
    fun provideSetupStakingFeeValidation(
        walletRepository: WalletRepository,
    ): SetupStakingFeeValidation {
        return EnoughToPayFeesValidation(
            feeExtractor = { it.maxFee },
            availableBalanceProducer = SetupStakingFeeValidation.assetBalanceProducer(
                walletRepository,
                originAddressExtractor = { it.controllerAddress },
                tokenTypeExtractor = { it.tokenType }
            ),
            errorProducer = { SetupStakingValidationFailure.CannotPayFee },
            extraAmountExtractor = { it.bondAmount ?: BigDecimal.ZERO }
        )
    }

    @Provides
    @FeatureScope
    fun provideElectionPeriodValidation(
        stakingRepository: StakingRepository
    ): SetupStakingElectionValidation {
        return SetupStakingElectionValidation(
            stakingRepository = stakingRepository,
            networkTypeProvider = { it.controllerAddress.networkType() },
            errorProducer = { SetupStakingValidationFailure.ElectionPeriod }
        )
    }

    @Provides
    @FeatureScope
    fun provideMinimumAmountValidation(
        stakingRepository: StakingRepository
    ) = MinimumAmountValidation(stakingRepository)

    @Provides
    @FeatureScope
    fun provideMaxNominatorsReachedValidation(
        stakingRepository: StakingRepository
    ) = SetupStakingMaximumNominatorsValidation(
        stakingRepository = stakingRepository,
        errorProducer = { SetupStakingValidationFailure.MaxNominatorsReached }
    )

    @Provides
    @FeatureScope
    fun provideSetupStakingValidationSystem(
        enoughToPayFeesValidation: SetupStakingFeeValidation,
        minimumAmountValidation: MinimumAmountValidation,
        maxNominatorsReachedValidation: SetupStakingMaximumNominatorsValidation,
        electionPeriodClosedValidation: SetupStakingElectionValidation
    ) = ValidationSystem(
        CompositeValidation(listOf(
            enoughToPayFeesValidation,
            minimumAmountValidation,
            maxNominatorsReachedValidation,
            electionPeriodClosedValidation
        ))
    )
}

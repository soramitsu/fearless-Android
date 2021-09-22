package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.MinimumAmountValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingMaximumNominatorsValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingPayload
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
                tokenTypeExtractor = { it.chainAsset }
            ),
            errorProducer = { SetupStakingValidationFailure.CannotPayFee },
            extraAmountExtractor = { it.bondAmount ?: BigDecimal.ZERO }
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
        errorProducer = { SetupStakingValidationFailure.MaxNominatorsReached },
        isAlreadyNominating = SetupStakingPayload::isAlreadyNominating
    )

    @Provides
    @FeatureScope
    fun provideSetupStakingValidationSystem(
        enoughToPayFeesValidation: SetupStakingFeeValidation,
        minimumAmountValidation: MinimumAmountValidation,
        maxNominatorsReachedValidation: SetupStakingMaximumNominatorsValidation
    ) = ValidationSystem(
        CompositeValidation(
            listOf(
                enoughToPayFeesValidation,
                minimumAmountValidation,
                maxNominatorsReachedValidation
            )
        )
    )
}

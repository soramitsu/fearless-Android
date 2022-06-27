package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import java.math.BigDecimal
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.MinimumAmountValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingMaximumNominatorsValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation
import jp.co.soramitsu.feature_wallet_api.domain.validation.assetBalanceProducer

@Module
class SetupStakingValidationsModule {

    @Provides
    @FeatureScope
    fun provideSetupStakingFeeValidation(
        stakingSharedState: StakingSharedState,
        walletRepository: WalletRepository,
        accountRepository: AccountRepository
    ): SetupStakingFeeValidation {
        return EnoughToPayFeesValidation(
            feeExtractor = { it.maxFee },
            availableBalanceProducer = SetupStakingFeeValidation.assetBalanceProducer(
                accountRepository,
                walletRepository,
                originAddressExtractor = { it.controllerAddress },
                chainAssetExtractor = { it.asset.token.configuration },
                stakingSharedState = stakingSharedState
            ),
            errorProducer = { SetupStakingValidationFailure.CannotPayFee },
            extraAmountExtractor = { it.bondAmount ?: BigDecimal.ZERO }
        )
    }

    @Provides
    @FeatureScope
    fun provideMinimumAmountValidation(
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = MinimumAmountValidation(stakingScenarioInteractor)

    @Provides
    @FeatureScope
    fun provideMaxNominatorsReachedValidation(
        stakingSharedState: StakingSharedState,
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = SetupStakingMaximumNominatorsValidation(
        stakingScenarioInteractor = stakingScenarioInteractor,
        errorProducer = { SetupStakingValidationFailure.MaxNominatorsReached },
        isAlreadyNominating = SetupStakingPayload::isAlreadyNominating,
        sharedState = stakingSharedState
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

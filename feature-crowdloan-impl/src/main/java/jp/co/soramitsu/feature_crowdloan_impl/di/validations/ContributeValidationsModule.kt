package jp.co.soramitsu.feature_crowdloan_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_impl.data.repository.ChainStateRepository
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.CapExceededValidation
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeEnoughToPayFeesValidation
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeExistentialDepositValidation
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationFailure
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationSystem
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.CrowdloanNotEndedValidation
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.MinContributionValidation
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants

@Module
class ContributeValidationsModule {

    @Provides
    @FeatureScope
    fun provideFeesValidation() = ContributeEnoughToPayFeesValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.asset.transferable },
        extraAmountExtractor = { it.contributionAmount },
        errorProducer = { ContributeValidationFailure.CannotPayFees }
    )

    @Provides
    @FeatureScope
    fun provideMinContributionValidation(
        crowdloanRepository: CrowdloanRepository,
    ) = MinContributionValidation(crowdloanRepository)

    @Provides
    @FeatureScope
    fun provideCapExceededValidation() = CapExceededValidation()

    @Provides
    @FeatureScope
    fun provideCrowdloanNotEndedValidation(
        chainStateRepository: ChainStateRepository,
        crowdloanRepository: CrowdloanRepository,
    ) = CrowdloanNotEndedValidation(chainStateRepository, crowdloanRepository)

    @Provides
    @FeatureScope
    fun provideExistentialWarningValidation(
        walletConstants: WalletConstants,
    ) = ContributeExistentialDepositValidation(
        walletConstants = walletConstants,
        totalBalanceProducer = { it.asset.total },
        feeProducer = { it.fee },
        extraAmountProducer = { it.contributionAmount },
        tokenProducer = { it.asset.token },
        errorProducer = { ContributeValidationFailure.ExistentialDepositCrossed },
    )

    @Provides
    @FeatureScope
    fun provideValidationSystem(
        contributeEnoughToPayFeesValidation: ContributeEnoughToPayFeesValidation,
        minContributionValidation: MinContributionValidation,
        capExceededValidation: CapExceededValidation,
        crowdloanNotEndedValidation: CrowdloanNotEndedValidation,
        contributeExistentialDepositValidation: ContributeExistentialDepositValidation,
    ) = ContributeValidationSystem(
        validation = CompositeValidation(
            validations = listOf(
                contributeEnoughToPayFeesValidation,
                minContributionValidation,
                capExceededValidation,
                crowdloanNotEndedValidation,
                contributeExistentialDepositValidation
            )
        )
    )
}

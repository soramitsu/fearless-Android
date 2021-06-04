package jp.co.soramitsu.feature_crowdloan_impl.di.validations

import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_impl.data.repository.ChainStateRepository
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.CapExceededValidation
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeEnoughToPayFeesValidation
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeExistentialDepositValidation
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidation
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationFailure
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationSystem
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.CrowdloanNotEndedValidation
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.MinContributionValidation
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.PublicCrowdloanValidation
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants

@Module
class ContributeValidationsModule {

    @Provides
    @IntoSet
    @FeatureScope
    fun provideFeesValidation(): ContributeValidation = ContributeEnoughToPayFeesValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.asset.transferable },
        extraAmountExtractor = { it.contributionAmount },
        errorProducer = { ContributeValidationFailure.CannotPayFees }
    )

    @Provides
    @IntoSet
    @FeatureScope
    fun provideMinContributionValidation(
        crowdloanRepository: CrowdloanRepository,
    ): ContributeValidation = MinContributionValidation(crowdloanRepository)

    @Provides
    @IntoSet
    @FeatureScope
    fun provideCapExceededValidation(): ContributeValidation = CapExceededValidation()

    @Provides
    @IntoSet
    @FeatureScope
    fun provideCrowdloanNotEndedValidation(
        chainStateRepository: ChainStateRepository,
        crowdloanRepository: CrowdloanRepository,
    ): ContributeValidation = CrowdloanNotEndedValidation(chainStateRepository, crowdloanRepository)

    @Provides
    @IntoSet
    @FeatureScope
    fun provideExistentialWarningValidation(
        walletConstants: WalletConstants,
    ): ContributeValidation = ContributeExistentialDepositValidation(
        walletConstants = walletConstants,
        totalBalanceProducer = { it.asset.total },
        feeProducer = { it.fee },
        extraAmountProducer = { it.contributionAmount },
        tokenProducer = { it.asset.token },
        errorProducer = { ContributeValidationFailure.ExistentialDepositCrossed },
    )

    @Provides
    @IntoSet
    @FeatureScope
    fun providePublicCrowdloanValidation(): ContributeValidation = PublicCrowdloanValidation()

    @Provides
    @FeatureScope
    fun provideValidationSystem(
        contributeValidations: @JvmSuppressWildcards Set<ContributeValidation>
    ) = ContributeValidationSystem(
        validation = CompositeValidation(contributeValidations.toList())
    )
}

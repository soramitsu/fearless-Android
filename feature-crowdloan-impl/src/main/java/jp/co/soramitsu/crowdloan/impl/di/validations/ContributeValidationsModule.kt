package jp.co.soramitsu.crowdloan.impl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.core.extrinsic.mortality.IChainStateRepository
import jp.co.soramitsu.crowdloan.api.data.repository.CrowdloanRepository
import jp.co.soramitsu.crowdloan.impl.domain.contribute.validations.CapExceededValidation
import jp.co.soramitsu.crowdloan.impl.domain.contribute.validations.ContributeEnoughToPayFeesValidation
import jp.co.soramitsu.crowdloan.impl.domain.contribute.validations.ContributeExistentialDepositValidation
import jp.co.soramitsu.crowdloan.impl.domain.contribute.validations.ContributeValidation
import jp.co.soramitsu.crowdloan.impl.domain.contribute.validations.ContributeValidationFailure
import jp.co.soramitsu.crowdloan.impl.domain.contribute.validations.ContributeValidationSystem
import jp.co.soramitsu.crowdloan.impl.domain.contribute.validations.CrowdloanNotEndedValidation
import jp.co.soramitsu.crowdloan.impl.domain.contribute.validations.MinContributionValidation
import jp.co.soramitsu.crowdloan.impl.domain.contribute.validations.PublicCrowdloanValidation
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants

@InstallIn(SingletonComponent::class)
@Module
class ContributeValidationsModule {

    @Provides
    @IntoSet
    fun provideFeesValidation(): ContributeValidation = ContributeEnoughToPayFeesValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.asset.transferable },
        extraAmountExtractor = { it.contributionAmount },
        errorProducer = { ContributeValidationFailure.CannotPayFees }
    )

    @Provides
    @IntoSet
    fun provideMinContributionValidation(
        crowdloanRepository: CrowdloanRepository
    ): ContributeValidation = MinContributionValidation(crowdloanRepository)

    @Provides
    @IntoSet
    fun provideCapExceededValidation(): ContributeValidation = CapExceededValidation()

    @Provides
    @IntoSet
    fun provideCrowdloanNotEndedValidation(
        chainStateRepository: IChainStateRepository,
        crowdloanRepository: CrowdloanRepository
    ): ContributeValidation = CrowdloanNotEndedValidation(chainStateRepository, crowdloanRepository)

    @Provides
    @IntoSet
    fun provideExistentialWarningValidation(
        walletConstants: WalletConstants
    ): ContributeValidation = ContributeExistentialDepositValidation(
        walletConstants = walletConstants,
        totalBalanceProducer = { it.asset.total.orZero() },
        feeProducer = { it.fee },
        extraAmountProducer = { it.contributionAmount },
        tokenProducer = { it.asset.token },
        errorProducer = { ContributeValidationFailure.ExistentialDepositCrossed }
    )

    @Provides
    @IntoSet
    fun providePublicCrowdloanValidation(): ContributeValidation = PublicCrowdloanValidation()

    @Provides
    fun provideValidationSystem(
        contributeValidations: @JvmSuppressWildcards Set<ContributeValidation>
    ) = ContributeValidationSystem(
        validation = CompositeValidation(contributeValidations.toList())
    )
}

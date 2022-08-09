package jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.validations

import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrError
import jp.co.soramitsu.featurecrowdloanapi.data.repository.CrowdloanRepository
import jp.co.soramitsu.featurewalletapi.domain.model.amountFromPlanks

class MinContributionValidation(
    private val crowdloanRepository: CrowdloanRepository
) : ContributeValidation {

    override suspend fun validate(value: ContributeValidationPayload): ValidationStatus<ContributeValidationFailure> {
        val chainAsset = value.asset.token.configuration

        val minContribution = value.customMinContribution ?: chainAsset.amountFromPlanks(crowdloanRepository.minContribution(chainAsset.chainId))

        return validOrError(value.contributionAmount >= minContribution) {
            ContributeValidationFailure.LessThanMinContribution(minContribution, chainAsset)
        }
    }
}

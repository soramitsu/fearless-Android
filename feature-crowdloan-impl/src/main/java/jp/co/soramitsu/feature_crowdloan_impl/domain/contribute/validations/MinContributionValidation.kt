package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations

import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrError
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks

class MinContributionValidation(
    private val crowdloanRepository: CrowdloanRepository
) : ContributeValidation {

    override suspend fun validate(value: ContributeValidationPayload): ValidationStatus<ContributeValidationFailure> {
        val token = value.asset.token

        val minContribution = token.amountFromPlanks(crowdloanRepository.minContribution())

        return validOrError(value.contributionAmount >= minContribution) {
            ContributeValidationFailure.LessThanMinContribution(minContribution, token)
        }
    }
}

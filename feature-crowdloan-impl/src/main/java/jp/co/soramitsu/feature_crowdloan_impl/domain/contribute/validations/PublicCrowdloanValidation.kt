package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations

import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrError

class PublicCrowdloanValidation : ContributeValidation {

    override suspend fun validate(value: ContributeValidationPayload): ValidationStatus<ContributeValidationFailure> {
        return validOrError(value.crowdloan.fundInfo.verifier == null) {
            ContributeValidationFailure.PrivateCrowdloanNotSupported
        }
    }
}

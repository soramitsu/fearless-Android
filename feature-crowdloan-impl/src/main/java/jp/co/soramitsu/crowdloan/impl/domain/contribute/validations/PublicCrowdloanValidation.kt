package jp.co.soramitsu.crowdloan.impl.domain.contribute.validations

import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrError

class PublicCrowdloanValidation : ContributeValidation {

    override suspend fun validate(value: ContributeValidationPayload): ValidationStatus<ContributeValidationFailure> {
        val isMoonbeam = value.crowdloan.parachainMetadata?.isMoonbeam == true
        val hasNoVerifier = value.crowdloan.fundInfo.verifier == null
        return validOrError(isMoonbeam || hasNoVerifier) {
            ContributeValidationFailure.PrivateCrowdloanNotSupported
        }
    }
}

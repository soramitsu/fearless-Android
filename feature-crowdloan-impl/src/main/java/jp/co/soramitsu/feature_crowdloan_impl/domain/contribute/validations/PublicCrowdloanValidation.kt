package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations

import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrError
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.isMoonbeam
import jp.co.soramitsu.feature_crowdloan_impl.BuildConfig

class PublicCrowdloanValidation : ContributeValidation {

    override suspend fun validate(value: ContributeValidationPayload): ValidationStatus<ContributeValidationFailure> {
        val isDebugMoonbeam = BuildConfig.DEBUG && value.crowdloan.parachainId.isMoonbeam()
        val hasNoVerifier = value.crowdloan.fundInfo.verifier == null
        return validOrError(isDebugMoonbeam || hasNoVerifier) {
            ContributeValidationFailure.PrivateCrowdloanNotSupported
        }
    }
}

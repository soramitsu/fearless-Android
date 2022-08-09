package jp.co.soramitsu.featurestakingimpl.domain.validations

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.featurestakingimpl.scenarios.StakingScenarioInteractor

class AccountRequiredValidation<P, E>(
    val stakingScenarioInteractor: StakingScenarioInteractor,
    val accountAddressExtractor: (P) -> String?,
    val errorProducer: (controllerAddress: String) -> E
) : Validation<P, E> {

    override suspend fun validate(value: P): ValidationStatus<E> {
        val accountAddress = accountAddressExtractor(value)

        val isValid = stakingScenarioInteractor.checkAccountRequiredValidation(accountAddress)

        return if (isValid) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProducer(accountAddress.orEmpty()))
        }
    }
}

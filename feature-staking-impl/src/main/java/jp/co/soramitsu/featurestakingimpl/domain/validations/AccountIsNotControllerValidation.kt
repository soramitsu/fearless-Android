package jp.co.soramitsu.featurestakingimpl.domain.validations

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.featurestakingimpl.scenarios.StakingScenarioInteractor

class AccountIsNotControllerValidation<P, E>(
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val controllerAddressProducer: (P) -> String,
    private val errorProducer: (P) -> E
) : Validation<P, E> {
    override suspend fun validate(value: P): ValidationStatus<E> {
        val controllerAddress = controllerAddressProducer(value)
        val accountIsNotController = stakingScenarioInteractor.accountIsNotController(controllerAddress)

        return if (accountIsNotController) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProducer(value))
        }
    }
}

package jp.co.soramitsu.feature_staking_impl.domain.validations

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

class ControllerRequiredValidation<P, E>(
    val accountRepository: AccountRepository,
    val controllerAddressExtractor: (P) -> String,
    val errorProducer: () -> E
) : Validation<P, E> {

    override suspend fun validate(value: P): ValidationStatus<E> {
        val controllerAddress = controllerAddressExtractor(value)

        return if (accountRepository.isAccountExists(controllerAddress)) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProducer())
        }
    }
}

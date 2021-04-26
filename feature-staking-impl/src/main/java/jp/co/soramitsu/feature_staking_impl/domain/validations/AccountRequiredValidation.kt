package jp.co.soramitsu.feature_staking_impl.domain.validations

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

class AccountRequiredValidation<P, E>(
    val accountRepository: AccountRepository,
    val accountAddressExtractor: (P) -> String,
    val errorProducer: (controllerAddress: String) -> E
) : Validation<P, E> {

    override suspend fun validate(value: P): ValidationStatus<E> {
        val accountAddress = accountAddressExtractor(value)

        return if (accountRepository.isAccountExists(accountAddress)) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProducer(accountAddress))
        }
    }
}

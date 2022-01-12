package jp.co.soramitsu.feature_staking_impl.domain.validations

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.state.chain

class AccountRequiredValidation<P, E>(
    val accountRepository: AccountRepository,
    val accountAddressExtractor: (P) -> String,
    val sharedState: StakingSharedState,
    val errorProducer: (controllerAddress: String) -> E
) : Validation<P, E> {

    override suspend fun validate(value: P): ValidationStatus<E> {
        val accountAddress = accountAddressExtractor(value)
        val chain = sharedState.chain()

        return if (accountRepository.isAccountExists(chain.accountIdOf(accountAddress))) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProducer(accountAddress))
        }
    }
}

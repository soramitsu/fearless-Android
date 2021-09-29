package jp.co.soramitsu.feature_staking_impl.domain.validations

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState

class AccountIsNotControllerValidation<P, E>(
    private val stakingRepository: StakingRepository,
    private val controllerAddressProducer: (P) -> String,
    private val sharedState: StakingSharedState,
    private val errorProducer: (P) -> E,
) : Validation<P, E> {
    override suspend fun validate(value: P): ValidationStatus<E> {
        val controllerAddress = controllerAddressProducer(value)
        val ledger = stakingRepository.ledger(sharedState.chainId(), controllerAddress)

        return if (ledger == null) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProducer(value))
        }
    }
}

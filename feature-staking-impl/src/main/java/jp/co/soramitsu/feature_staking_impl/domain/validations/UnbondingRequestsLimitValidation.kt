package jp.co.soramitsu.feature_staking_impl.domain.validations

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_staking_api.domain.model.StakingLedger
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor

private const val UNLOCKING_LIMIT = 32

class UnbondingRequestsLimitValidation<P, E>(
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    val errorProducer: (limit: Int) -> E
) : Validation<P, E> {

    override suspend fun validate(value: P): ValidationStatus<E> {
        val ledger: StakingLedger? = stakingScenarioInteractor.ledger()

        val isValid = ledger == null || ledger.unlocking.size < UNLOCKING_LIMIT
        return if (isValid) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProducer(UNLOCKING_LIMIT))
        }
    }
}

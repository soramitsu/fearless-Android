package jp.co.soramitsu.feature_staking_impl.domain.validations

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Election
import kotlinx.coroutines.flow.first

class ElectionPeriodClosedValidation<P, E>(
    val stakingRepository: StakingRepository,
    val networkTypeProvider: (P) -> Node.NetworkType,
    val errorProducer: () -> E
) : Validation<P, E> {

    override suspend fun validate(value: P): ValidationStatus<E> {
        val election = stakingRepository.electionFlow(networkTypeProvider(value)).first()

        return if (election == Election.CLOSED) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProducer())
        }
    }
}

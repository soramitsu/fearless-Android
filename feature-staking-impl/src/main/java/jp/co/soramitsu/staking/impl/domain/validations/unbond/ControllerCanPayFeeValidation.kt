package jp.co.soramitsu.staking.impl.domain.validations.unbond

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.wallet.impl.domain.validation.AmountProducer

class ControllerCanPayFeeValidation<P, E>(
    private val feeExtractor: AmountProducer<P>,
    private val availableControllerBalanceProducer: AmountProducer<P>,
    private val errorProducer: () -> E
) : Validation<P, E> {
    override suspend fun validate(value: P): ValidationStatus<E> {
        return if (feeExtractor(value) < availableControllerBalanceProducer(value)) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProducer())
        }
    }
}

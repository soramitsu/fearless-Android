package jp.co.soramitsu.staking.impl.domain.validations.unbond

import java.math.BigDecimal
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.wallet.impl.domain.validation.AmountProducer

class ControllerCanPayFeeValidation<P, E>(
    private val feeExtractor: AmountProducer<P>,
    private val availableControllerBalanceProducer: suspend (P) -> BigDecimal?,
    private val errorProducer: () -> E
) : Validation<P, E> {
    override suspend fun validate(value: P): ValidationStatus<E> {
        val fee = feeExtractor(value)
        val available = availableControllerBalanceProducer(value)
        return if (available != null && fee.signum() >= 0 && fee < available) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProducer())
        }
    }
}

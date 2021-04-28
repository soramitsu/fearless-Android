package jp.co.soramitsu.feature_staking_impl.domain.validations

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import java.math.BigDecimal

class NotZeroAmountValidation <P, E>(
    val amountExtractor: (P) -> BigDecimal,
    val errorProvider: () -> E
) : Validation<P, E> {

    override suspend fun validate(value: P): ValidationStatus<E> {
        return if (amountExtractor(value) > BigDecimal.ZERO) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProvider())
        }
    }
}

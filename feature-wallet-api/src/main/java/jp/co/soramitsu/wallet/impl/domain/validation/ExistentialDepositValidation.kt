package jp.co.soramitsu.wallet.impl.domain.validation

import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.common.validation.validOrWarning
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks

class ExistentialDepositValidation<P, E>(
    private val totalBalanceProducer: AmountProducer<P>,
    private val feeProducer: AmountProducer<P>,
    private val extraAmountProducer: AmountProducer<P>,
    private val tokenProducer: TokenProducer<P>,
    private val errorProducer: (edAmount: String) -> E,
    private val walletConstants: WalletConstants
) : Validation<P, E> {

    override suspend fun validate(value: P): ValidationStatus<E> {
        val token = tokenProducer(value)
        val existentialDepositInPlanks = walletConstants.existentialDeposit(token.configuration).orZero()
        val existentialDeposit = token.amountFromPlanks(existentialDepositInPlanks)

        val totalBalance = totalBalanceProducer(value)
        val fee = feeProducer(value)
        val extraAmount = extraAmountProducer(value)

        return validOrWarning(totalBalance - fee - extraAmount >= existentialDeposit) {
            errorProducer.invoke(existentialDeposit.formatCrypto(token.configuration.symbol))
        }
    }
}

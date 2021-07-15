package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import java.math.BigDecimal

data class SubqueryElement(
    val hash: String,
    val address: String,
    val accountName: String?,
    val operation: Operation,
    val amount: BigDecimal,
    val time: Long,
    val tokenType: Token.Type,
    val extra: String? = null,
    val nextPageCursor: String? = null,
    val displayAddress: String? = null,
    val isIncome: Boolean = true // FIXME
) {

    val formattedAmount = createFormattedAmount()

    fun getOperationHeader() = if (operation is Operation.Extrinsic) operation.callName else null

    fun getElementDescription() = operation.action

    private fun createFormattedAmount(): String {
        val withoutSign = amount.formatTokenAmount(tokenType)
        val sign = if (isIncome) '+' else '-'

        return sign + withoutSign
    }

    sealed class Operation(val action: String) {
        class Extrinsic(val callName: String, val module: String) : Operation(module)
        class Reward : Operation("Reward")
        class Transfer : Operation("Transfer")
    }
}

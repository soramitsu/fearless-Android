package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.feature_wallet_api.R
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
    val isIncome: Boolean = true
) {

    val formattedAmount = createFormattedAmount()

    fun getOperationHeader() = operation.call

    fun getElementDescription() = operation.module

    fun getOperationIcon(): Int? = when (operation) {
        is Operation.Reward -> R.drawable.ic_staking
        else -> null
    }

    private fun createFormattedAmount(): String {
        val withoutSign = amount.formatTokenAmount(tokenType)
        val sign = if (isIncome) '+' else '-'

        return sign + withoutSign
    }

    sealed class Operation(val call: String?, val module: String?) {
        class Extrinsic(val callName: String, val moduleName: String) : Operation(callName, moduleName)
        class Reward : Operation("Reward", "Staking")
        class Transfer : Operation(null, "Transfer")
    }
}

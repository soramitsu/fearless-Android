package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import java.math.BigDecimal

data class SubqueryElement(
    val hash : String,
    val address: String,
    val accountName: String?,
    val operation: String,
    val amount: BigDecimal,
    val time: Long,
    val tokenType: Token.Type,
    val extra: String? = null,
    val nextPageCursor: String? = null,
    val isIncome: Boolean = true // FIXME
){

    val formattedAmount = createFormattedAmount()

    private fun createFormattedAmount(): String {
        val withoutSign = amount.formatTokenAmount(tokenType)
        val sign = if (isIncome) '+' else '-'

        return sign + withoutSign
    }
}

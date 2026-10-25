package jp.co.soramitsu.wallet.impl.data.network.model.request

import com.google.gson.JsonPrimitive
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter

class GiantsquidHistoryRequest(
    accountAddress: String,
    limit: Int = 100,
    offset: Int = 0,
    filters: Set<TransactionFilter> = setOf(
        TransactionFilter.TRANSFER,
        TransactionFilter.REWARD,
        TransactionFilter.EXTRINSIC
    )
) {
    init {
        require(accountAddress.isNotBlank() && accountAddress.length <= MAX_ACCOUNT_ADDRESS_LENGTH) {
            "Giantsquid account address must be non-blank and bounded"
        }
        require(limit in 1..MAX_PAGE_SIZE) { "Giantsquid history limit must be between 1 and $MAX_PAGE_SIZE" }
        require(offset >= 0) { "Giantsquid history offset must not be negative" }
        require(filters.isNotEmpty()) { "Giantsquid history filters must not be empty" }
    }

    val query = buildString {
        val accountAddressLiteral = JsonPrimitive(accountAddress).toString()

        appendLine("query MyQuery {")

        if (TransactionFilter.TRANSFER in filters) {
            appendLine(
                "  transfers(where: {account: {id_eq: $accountAddressLiteral}}, " +
                    "orderBy: id_DESC, limit: $limit, offset: $offset) {"
            )
            appendLine("    id")
            appendLine("    transfer {")
            appendLine("      amount")
            appendLine("      blockNumber")
            appendLine("      extrinsicHash")
            appendLine("      from { id }")
            appendLine("      to { id }")
            appendLine("      timestamp")
            appendLine("      success")
            appendLine("      id")
            appendLine("    }")
            appendLine("    direction")
            appendLine("  }")
        }

        if (TransactionFilter.REWARD in filters) {
            appendLine(
                "  rewards(where: {account: {id_eq: $accountAddressLiteral}}, " +
                    "orderBy: id_DESC, limit: $limit, offset: $offset) {"
            )
            appendLine("    id")
            appendLine("    timestamp")
            appendLine("    blockNumber")
            appendLine("    extrinsicHash")
            appendLine("    amount")
            appendLine("    era")
            appendLine("    validatorId")
            appendLine("    account { id }")
            appendLine("  }")
        }

        if (TransactionFilter.EXTRINSIC in filters) {
            appendLine(
                "  bonds(where: {accountId_eq: $accountAddressLiteral}, " +
                    "orderBy: id_DESC, limit: $limit, offset: $offset) {"
            )
            appendLine("    id")
            appendLine("    accountId")
            appendLine("    amount")
            appendLine("    blockNumber")
            appendLine("    extrinsicHash")
            appendLine("    success")
            appendLine("    timestamp")
            appendLine("    type")
            appendLine("  }")
            appendLine(
                "  slashes(where: {accountId_eq: $accountAddressLiteral}, " +
                    "orderBy: id_DESC, limit: $limit, offset: $offset) {"
            )
            appendLine("    id")
            appendLine("    accountId")
            appendLine("    amount")
            appendLine("    blockNumber")
            appendLine("    era")
            appendLine("    timestamp")
            appendLine("  }")
        }

        append('}')
    }

    private companion object {
        const val MAX_ACCOUNT_ADDRESS_LENGTH = 512
        const val MAX_PAGE_SIZE = 100
    }
}

package jp.co.soramitsu.feature_wallet_impl.data.network.model.request

import android.annotation.SuppressLint
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionFilter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.allFiltersIncluded
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.SubqueryExpressions.and
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.SubqueryExpressions.anyOf
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.SubqueryExpressions.not
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.SubqueryExpressions.or

private class ModuleRestriction(
    val moduleName: String,
    val restrictedCalls: List<String>
)

private val EXTRINSIC_RESTRICTIONS = listOf(
    ModuleRestriction(
        moduleName = "balances",
        restrictedCalls = listOf(
            "transfer",
            "transferKeepAlive",
            "forceTransfer"
        )
    )
)

class SubqueryHistoryRequest(
    accountAddress: String,
    pageSize: Int = 1,
    cursor: String? = null,
    filters: Set<TransactionFilter>
) {
    val query = """
    {
        query {
            historyElements(
                after: ${if (cursor == null) null else "\"$cursor\""},
                first: $pageSize,
                orderBy: TIMESTAMP_DESC,
                filter: { 
                    address:{ equalTo: "$accountAddress"},
                    ${filters.toQueryFilter()}
                }
            ) {
                pageInfo {
                    startCursor,
                    endCursor
                },
                nodes {
                    id
                    timestamp
                    address
                    reward
                    extrinsic
                    transfer
                }
            }
        }
    }

    """.trimIndent()

    /*
        or: [ {transfer: { notEqualTo: "null"} },  {extrinsic: { notEqualTo: "null"} } ]
     */
    private fun Set<TransactionFilter>.toQueryFilter(): String {

        // optimize query in case all filters are on
        if (allFiltersIncluded()) {
            return ""
        }

        val typeExpressions = map {
            if (it == TransactionFilter.EXTRINSIC) {
                createExtrinsicExpression()
            } else {
                hasType(it.filterName)
            }
        }

        val result = anyOf(typeExpressions)

        return result
    }

    private fun createExtrinsicExpression(): String {
        val exists = hasType(TransactionFilter.EXTRINSIC.filterName)

        val restrictedModulesList = EXTRINSIC_RESTRICTIONS.map {
            val restrictedCallsExpressions = it.restrictedCalls.map(::callNamed)

            and(
                moduleNamed(it.moduleName),
                anyOf(restrictedCallsExpressions)
            )
        }

        val hasRestrictedModules = or(restrictedModulesList)

        return and(
            exists,
            not(hasRestrictedModules)
        )
    }

    private fun callNamed(callName: String) = "extrinsic: {contains: {call: \"$callName\"}}"
    private fun moduleNamed(moduleName: String) = "extrinsic: {contains: {module: \"$moduleName\"}}"
    private fun hasType(typeName: String) = "$typeName: {isNull: false}"

    private val TransactionFilter.filterName
        @SuppressLint("DefaultLocale")
        get() = name.toLowerCase()
}

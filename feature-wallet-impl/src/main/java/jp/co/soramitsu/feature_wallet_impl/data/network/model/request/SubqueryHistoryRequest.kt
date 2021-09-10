package jp.co.soramitsu.feature_wallet_impl.data.network.model.request

import android.annotation.SuppressLint
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionFilter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.allFiltersIncluded

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

        return joinToString(prefix = "or: [", postfix = "]", separator = ",") {
            "{ ${it.filterName}: {  notEqualTo: \"null\" } }"
        }
    }

    private val TransactionFilter.filterName
        @SuppressLint("DefaultLocale")
        get() = name.toLowerCase()
}

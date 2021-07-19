package jp.co.soramitsu.feature_wallet_impl.data.network.model.request

class SubqueryHistoryElementByAddressRequest(accountAddress: String, pageSize: Int = 1, cursor: String? = null) {
    val query = """
    {
        query {
            historyElements(
                after: ${ if (cursor == null) null else "\"$cursor\""},
                first: $pageSize,
                filter: { 
                    address:{ equalTo: "$accountAddress"}
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
}

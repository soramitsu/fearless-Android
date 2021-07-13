package jp.co.soramitsu.feature_wallet_impl.data.network.model.request

class SubqueryHistoryElementByAddressRequest (accountAddress: String, pageSize: Int = 1, pageCount: Int = 0) {
    val query = """
    {
        query {
            historyElements(
                first: $pageSize, 
                offset: ${pageSize * pageCount},
                filter: { 
                    address:{ equalTo: "$accountAddress"}
                },
            ) {
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

package jp.co.soramitsu.wallet.impl.data.network.model.request

import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter

class ReefRequestBuilder(
    private val filters: Set<TransactionFilter>,
    private val accountAddress: String,
    private val limit: Int = 50,
    private val transfersOffset: String? = null,
    private val rewardsOffset: String? = null
) {
    fun buildRequest(): ReefHistoryRequest {
        val query = StringBuilder()
        if (filters.contains(TransactionFilter.TRANSFER)) {
            query.append(
                """
                transfersConnection(where: {AND: [{type_eq: Native}, {OR: [{from: {id_eq: "$accountAddress"}}, {to: {id_eq: "$accountAddress"}}]}]}, orderBy: timestamp_DESC, first: $limit, after: $transfersOffset) {
                    edges {
                      node {
                        id
                        amount
                        feeAmount
                        type
                        timestamp
                        success
                        denom
                        to {
                          id
                        }
                        from {
                          id
                        }
                        extrinsic {
                          hash
                        }
                      }
                    }
                    pageInfo {
                      endCursor
                      hasNextPage
                      startCursor
                    }
                  }
            """.trimIndent()
            )
        }
        if (filters.contains(TransactionFilter.REWARD)) {
            query.append(
                """
                stakingsConnection(orderBy: timestamp_DESC, where: {AND: {signer: {id_eq: "$accountAddress"}, amount_gt: "0"}}, first: $limit, after: $rewardsOffset) {
                    edges {
                      node {
                        id
                        type
                        amount
                        timestamp
                        signer {
                          id
                        }
                      }
                    }
                    totalCount
                    pageInfo {
                      endCursor
                      hasNextPage
                    }
                }
            """.trimIndent()
            )
        }

        return ReefHistoryRequest(query.toString())
    }

}

class ReefHistoryRequest(
    query: String
) {
    val query = """
query MyQuery {
  $query
}
""".trimIndent()
}

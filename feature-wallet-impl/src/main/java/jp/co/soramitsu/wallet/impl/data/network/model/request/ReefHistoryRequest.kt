package jp.co.soramitsu.wallet.impl.data.network.model.request

import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter

class ReefRequestBuilder(
    private val filters: Set<TransactionFilter>,
    private val accountAddress: String,
    private val limit: Int = 50,
    private val transfersOffset: String? = null
) {
    fun buildRequest(): ReefHistoryRequest {
        val offset = transfersOffset?.let { "\"$it\"" }
        val query = StringBuilder()
        if (filters.contains(TransactionFilter.TRANSFER)) {
            query.append(
                """
                transfersConnection(where: {AND: [{type_eq: Native}, {OR: [{from: {id_eq: "$accountAddress"}}, {to: {id_eq: "$accountAddress"}}]}]}, orderBy: timestamp_DESC, first: $limit, after: $offset) {
                    edges {
                      node {
                        amount
                        timestamp
                        success
                        to {
                          id
                        }
                        from {
                          id
                        }
                        signedData
                        extrinsicHash
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
                stakingsConnection(orderBy: timestamp_DESC, where: {AND: {signer: {id_eq: "$accountAddress"}, amount_gt: "0"}}, first: $limit, after: $offset) {
                    edges {
                      node {
                        id
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
        if (filters.contains(TransactionFilter.EXTRINSIC)) {
            query.append(
                """
                  extrinsicsConnection(orderBy: id_ASC, where: {signer_eq: "$accountAddress"}, first: $limit, after: $offset) {
                    edges {
                      node {
                        id
                        hash
                        method
                        section
                        signedData
                        status
                        signer
                        timestamp
                        type
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

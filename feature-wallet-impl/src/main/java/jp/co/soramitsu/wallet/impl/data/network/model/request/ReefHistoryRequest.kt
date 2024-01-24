package jp.co.soramitsu.wallet.impl.data.network.model.request

class ReefHistoryRequest(
    accountAddress: String,
    limit: Int = 50,
    offset: String? = null
) {
    val query = """
query MyQuery {
  transfersConnection(where: {AND: [{type_eq: Native}, {OR: [{from: {id_eq: "$accountAddress"}}, {to: {id_eq: "$accountAddress"}}]}]}, orderBy: timestamp_DESC, first: $limit, after: $offset) {
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
}
""".trimIndent()
}

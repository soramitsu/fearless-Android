package jp.co.soramitsu.wallet.impl.data.network.model.request

class SubsquidHistoryRequest(
    accountAddress: String,
    limit: Int = 100,
    offset: String? = null
) {
    val query = """
    query MyQuery {
  historyElementsConnection(where: {address_eq: "$accountAddress"}, orderBy: timestamp_DESC, first: $limit, after: $offset) {
    pageInfo {
      hasNextPage
      endCursor
    }
    edges {
      node {
        timestamp
        id
        extrinsicIdx
        extrinsicHash
        address
        success
        transfer {
          amount
          fee
          from
          to
        }
        reward {
          amount
          era
          stash
        }
      }
    }
  }
}

    """.trimIndent()
}

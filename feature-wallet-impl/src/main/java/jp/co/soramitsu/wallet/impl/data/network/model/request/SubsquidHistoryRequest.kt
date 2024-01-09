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
        blockNumber
        address
        extrinsic {
          call
          fee
          hash
          module
          success
        }
        transfer {
          amount
          eventIdx
          fee
          from
          success
          to
        }
        reward {
          amount
          era
          eventIdx
          isReward
          stash
          validator
        }
      }
    }
  }
}

    """.trimIndent()
}

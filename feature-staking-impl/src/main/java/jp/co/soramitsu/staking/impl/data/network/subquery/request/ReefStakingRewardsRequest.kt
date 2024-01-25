package jp.co.soramitsu.staking.impl.data.network.subquery.request

class ReefStakingRewardsRequest(address: String, pageSize: Int = 100, offset: String? = null) {
    val query = """
        query MyQuery {
          stakingsConnection(orderBy: timestamp_DESC, where: {signer: {id_eq: "$address"}}, first: $pageSize, after: $offset) {
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
  }
    """.trimIndent()
}
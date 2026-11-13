package jp.co.soramitsu.staking.impl.data.network.subquery.request

import com.google.gson.Gson

private const val MAX_REEF_REWARDS_CURSOR_LENGTH = 4096

class ReefStakingRewardsRequest(address: String, pageSize: Int = 100, offset: String? = null) {
    init {
        require(offset == null || offset.length <= MAX_REEF_REWARDS_CURSOR_LENGTH) { "Invalid Reef rewards cursor" }
    }

    val query = """
        query MyQuery {
          stakingsConnection(orderBy: timestamp_DESC, where: {AND: {signer: {id_eq: "$address"}, amount_gt: "0"}}, first: $pageSize, after: ${Gson().toJson(offset)}) {
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

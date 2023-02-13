package jp.co.soramitsu.wallet.impl.data.network.model.request

class SubsquidHistoryRequest(
    accountAddress: String,
    limit: Int = 100,
    offset: Int = 0
) {
    val query = """
    query MyQuery {
      historyElements(where: {address_eq: "$accountAddress"}, orderBy: timestamp_DESC, limit: $limit, offset: $offset) {
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
    """.trimIndent()
}

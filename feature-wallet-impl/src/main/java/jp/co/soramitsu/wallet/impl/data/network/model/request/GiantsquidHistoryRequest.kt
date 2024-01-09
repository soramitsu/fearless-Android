package jp.co.soramitsu.wallet.impl.data.network.model.request

class GiantsquidHistoryRequest(
    accountAddress: String,
    limit: Int = 100,
    offset: Int = 0
) {
    val query = """
    query MyQuery {
      transfers(where: {account: {id_eq: "$accountAddress"}}, orderBy: id_DESC) {
        id
        transfer {
          amount
          blockNumber
          extrinsicHash
          from {
            id
          }
          to {
            id
          }
          timestamp
          success
          id
        }
        direction
      }
    }
    """.trimIndent()
}

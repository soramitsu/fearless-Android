package jp.co.soramitsu.staking.impl.data.network.subquery.request

class SubsquidCollatorsApyRequest {
    val query = """
   query MyQuery {
      stakers(where: {role_eq: "collator"}) {
        stashId
        apr24h
      }
    }
    """.trimIndent()
}

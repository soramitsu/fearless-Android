package jp.co.soramitsu.feature_staking_impl.data.network.subscan.request

import java.math.BigInteger

class StakingEraValidatorInfosRequest(eraFrom: BigInteger, eraTo: BigInteger, accountAddress: String) {
    val query = """
        {
            query {
                eraValidatorInfos(
                    filter:{
                        era:{ greaterThanOrEqualTo: $eraFrom, lessThanOrEqualTo: $eraTo},
                        others:{ contains:[{who: "$accountAddress"}]}
                    }
                ) {
                    nodes {
                        id
                        address
                        era
                        total
                        own
                    }
                }
            }
        }
    """.trimIndent()
}

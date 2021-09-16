package jp.co.soramitsu.common.data.network.subquery

import java.math.BigInteger

class EraValidatorInfoQueryResponse(val query: EraValidatorInfo?) {
    class EraValidatorInfo(val eraValidatorInfos: Nodes?) {
        class Nodes(val nodes: List<Node>?) {
            class Node(
                val id: String,
                val address: String,
                val era: BigInteger,
                val total: String,
                val own: String,
            )
        }
    }
}

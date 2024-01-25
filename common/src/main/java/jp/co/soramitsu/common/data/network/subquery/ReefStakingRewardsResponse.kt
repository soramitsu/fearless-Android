package jp.co.soramitsu.common.data.network.subquery

import java.math.BigInteger

class ReefStakingRewardsResponse (
    val stakingsConnection: ReefRewardsConnection
)

class SubsquidPageInfo(
    val hasNextPage: Boolean,
    val endCursor: String
)

class ReefRewardsConnection(
    val edges: List<ReefRewardsEdge>,
    val pageInfo: SubsquidPageInfo
)
class ReefRewardsEdge(
    val node: ReefRewardsNode
)
class ReefRewardsNode(
    val id: String,
    val type: String,
    val amount: BigInteger,
    val timestamp: String,
    val signer: ReefAddress
)

class ReefAddress(
    val id: String
)
//"stakingsConnection": {
//            "edges": [
//                {
//                    "node": {
//                        "id": "0008435587-000012-2d7db",
//                        "type": "Reward",
//                        "amount": "0",
//                        "timestamp": "2024-01-24T13:36:30.000000Z",
//                        "signer": {
//                            "id": "5GecoStYi2bHzKz6LwE2LWa8MWJxaZYAGjx2WeH8r4RTnQ6e"
//                        }
//                    }
//                },
//                {
//                    "node": {
//                        "id": "0008435117-000011-2e965",
//                        "type": "Reward",
//                        "amount": "306857767686205",
//                        "timestamp": "2024-01-24T12:18:10.000000Z",
//                        "signer": {
//                            "id": "5GecoStYi2bHzKz6LwE2LWa8MWJxaZYAGjx2WeH8r4RTnQ6e"
//                        }
//                    }
//                },
//                {
//                    "node": {
//                        "id": "0008427594-000032-42e47",
//                        "type": "Reward",
//                        "amount": "0",
//                        "timestamp": "2024-01-23T15:24:20.000000Z",
//                        "signer": {
//                            "id": "5GecoStYi2bHzKz6LwE2LWa8MWJxaZYAGjx2WeH8r4RTnQ6e"
//                        }
//                    }
//                },
//                {
//                    "node": {
//                        "id": "0008427594-000015-42e47",
//                        "type": "Reward",
//                        "amount": "0",
//                        "timestamp": "2024-01-23T15:24:20.000000Z",
//                        "signer": {
//                            "id": "5GecoStYi2bHzKz6LwE2LWa8MWJxaZYAGjx2WeH8r4RTnQ6e"
//                        }
//                    }
//                },
//                {
//                    "node": {
//                        "id": "0008427594-000024-42e47",
//                        "type": "Reward",
//                        "amount": "0",
//                        "timestamp": "2024-01-23T15:24:20.000000Z",
//                        "signer": {
//                            "id": "5GecoStYi2bHzKz6LwE2LWa8MWJxaZYAGjx2WeH8r4RTnQ6e"
//                        }
//                    }
//                }
//            ],
//            "totalCount": 198,
//            "pageInfo": {
//                "endCursor": "5",
//                "hasNextPage": true
//            }
//        }
//    }

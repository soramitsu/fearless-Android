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
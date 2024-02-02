package jp.co.soramitsu.wallet.impl.data.network.model.response

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.subquery.ReefAddress
import jp.co.soramitsu.common.data.network.subquery.ReefRewardsConnection

data class ReefHistoryResponse(val transfersConnection: ReefElementsConnection? = null, val stakingsConnection: ReefRewardsConnection? = null)

class ReefElementsConnection(
    val pageInfo: SubsquidPageInfo,
    val edges: List<ReefHistoryEdge>
)

class ReefHistoryEdge(val node: ReefHistoryNode)
class ReefHistoryNode(
    val id: String,
    val amount: BigInteger,
    val feeAmount: BigInteger,
    val type: String,
    val timestamp: String,
    val success: Boolean,
    val to: ReefAddress,
    val from: ReefAddress,
    val extrinsicHash: String?
)


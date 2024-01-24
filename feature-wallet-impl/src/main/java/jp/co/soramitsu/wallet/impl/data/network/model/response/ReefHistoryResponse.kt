package jp.co.soramitsu.wallet.impl.data.network.model.response

import java.math.BigInteger

data class ReefHistoryResponse(val transfersConnection: ReefElementsConnection)

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
    val denom: String,
    val to: ReefAddress,
    val from: ReefAddress,
    val extrinsic: ReefExtrinsic?
)

class ReefExtrinsic(val hash: String)

class ReefAddress(
    val id: String
)

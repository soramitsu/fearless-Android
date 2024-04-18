package jp.co.soramitsu.wallet.impl.data.network.model.response

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.subquery.ReefAddress
import jp.co.soramitsu.common.data.network.subquery.ReefRewardsConnection

data class ReefHistoryResponse(
    val transfersConnection: ReefElementsConnection? = null,
    val stakingsConnection: ReefRewardsConnection? = null,
    val extrinsicsConnection: ReefExtrinsicConnection? = null
)

class ReefElementsConnection(
    val pageInfo: SubsquidPageInfo,
    val edges: List<ReefHistoryEdge>
)

class ReefHistoryEdge(val node: ReefHistoryNode)
class ReefHistoryNode(
    val id: String? = null,
    val amount: BigInteger,
    val type: String,
    val timestamp: String,
    val success: Boolean,
    val to: ReefAddress,
    val from: ReefAddress,
    val extrinsicHash: String?,
    val signedData: ReefSignedData?
)

class ReefExtrinsicConnection(
    val pageInfo: SubsquidPageInfo,
    val edges: List<ReefExtrinsicEdge>
)

class ReefExtrinsicEdge(val node: ReefExtrinsicNode)
class ReefExtrinsicNode(
    val id: String,
    val hash: String,
    val method: String,
    val section: String,
    val signedData: ReefSignedData?,
    val status: String,
    val signer: String,
    val timestamp: String,
    val type: String
)

class ReefSignedData(val fee: ReefFeeData?)

class ReefFeeData(
    val partialFee: BigInteger?
)
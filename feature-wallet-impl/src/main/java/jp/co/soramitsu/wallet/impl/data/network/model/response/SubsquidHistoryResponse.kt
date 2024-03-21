package jp.co.soramitsu.wallet.impl.data.network.model.response

import java.math.BigInteger

class SubsquidHistoryElementsConnectionResponse(
    val historyElementsConnection: HistoryElementsConnection
)

class HistoryElementsConnection(val pageInfo: SubsquidPageInfo,
                                val edges: List<SubsquidHistoryEdge>)

class SubsquidPageInfo(
    val hasNextPage: Boolean,
    val endCursor: String
)

class SubsquidHistoryEdge(val node: SubsquidHistoryResponse.HistoryElement)

class SubsquidHistoryResponse(val historyElements: Array<HistoryElement>) {
    class HistoryElement(
        val id: String,
        val extrinsicIdx: String?,
        val extrinsicHash: String?,
        val timestamp: Long,
        val address: String,
        val success: Boolean,
        val reward: SubsquidReward?,
        val transfer: SubsquidTransfer?
    ) {

        val timestampMillis: Long
            get() = timestamp * 1000

        class SubsquidReward(
            val amount: String,
            val era: Int?,
            val stash: String?,
            val validator: String?
        )

        class SubsquidTransfer(
            val amount: String,
            val to: String,
            val from: String,
            val fee: BigInteger?
        )
    }
}

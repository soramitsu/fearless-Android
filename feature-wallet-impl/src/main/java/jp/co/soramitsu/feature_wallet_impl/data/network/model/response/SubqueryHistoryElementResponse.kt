package jp.co.soramitsu.feature_wallet_impl.data.network.model.response

import java.math.BigInteger

class SubqueryHistoryElementResponse(val query: Query) {
    class Query(val historyElements: HistoryElements) {

        class HistoryElements(val nodes: Array<Node>, val pageInfo: PageInfo) {
            class PageInfo(
                val startCursor: String,
                val endCursor: String?
            )

            class Node(
                val id: String,
                val timestamp: String,
                val address: String,
                val reward: Rewards?,
                val transfer: Transfer?,
                val extrinsic: Extrinsic?
            ) {
                class Rewards(
                    val era: Int,
                    val amount: BigInteger,
                    val isReward: Boolean,
                    val validator: String,
                )

                class Transfer(
                    val amount: BigInteger,
                    val to: String,
                    val from: String,
                    val fee: BigInteger,
                    val block: String,
                    val success: Boolean,
                    val extrinsicHash: String? // nullable since not all transfers not hash hash on SubQuery
                )

                class Extrinsic(
                    val hash: String,
                    val module: String,
                    val call: String,
                    val fee: BigInteger,
                    val success: Boolean
                )
            }
        }
    }
}

package jp.co.soramitsu.feature_wallet_impl.data.network.model.response

import jp.co.soramitsu.feature_wallet_api.domain.model.Extrinsic
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal
import java.math.BigInteger

class SubqueryHistoryElementResponse(val query: Query) {
    class Query(val historyElements: HistoryElements) {

        class HistoryElements(val nodes: Array<Node>, val pageInfo: PageInfo) {
            class PageInfo(
                val startCursor: String,
                val endCursor: String,
                val hasNextPage: Boolean
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
                    val amount: String,
                    val isReward: Boolean,
                    val validator: String
                )

                class Transfer(
                    val amount: String,
                    val to: String,
                    val from: String,
                    val fee: String,
                    val block: String,
                    val extrinsicId: String
                )

                class RewardSlash(
                    val amount: String,
                    val isReward: Boolean,
                    val era: Int,
                    val validator: String
                )
            }
        }
    }
}

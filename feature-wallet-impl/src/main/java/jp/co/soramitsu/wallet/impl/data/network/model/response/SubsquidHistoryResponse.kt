package jp.co.soramitsu.wallet.impl.data.network.model.response

import java.math.BigInteger

class SubsquidHistoryResponse(val historyElements: Array<HistoryElement>) {
    class HistoryElement(
        val id: String,
        val blockNumber: Int,
        val extrinsicIdx: String?,
        val extrinsicHash: String?,
        val timestamp: Long,
        val address: String,
        val reward: SubsquidRewardOrSlash?,
        val extrinsic: SubsquidExtrinsic?,
        val transfer: SubsquidTransfer?
    ) {

        class SubsquidRewardOrSlash(
            val eventIdx: String,
            val amount: String,
            val isReward: Boolean,
            val era: Int?,
            val stash: String?,
            val validator: String?
        )

        class SubsquidExtrinsic(
            val hash: String,
            val module: String,
            val call: String,
            val fee: String,
            val success: Boolean
        )

        class SubsquidTransfer(
            val amount: String,
            val to: String,
            val from: String,
            val fee: BigInteger?,
            val eventIdx: String,
            val success: Boolean
        )
    }
}

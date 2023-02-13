package jp.co.soramitsu.wallet.impl.data.network.model.response

class SubsquidHistoryResponse(val historyElements: Array<HistoryElement>) {
    class HistoryElement(
        val id: String,
        val timestamp: String,
        val address: String,
        val reward: SubsquidRewardOrSlash?,
        val extrinsic: SubsquidExtrinsic?,
        val transfer: SubsquidTransfer?
    ) {

        class SubsquidRewardOrSlash(
            val amount: String,
            val isReward: Boolean,
            val era: Int?,
            val validator: String?,
            val stash: String?,
            val eventIdx: String?,
            val assetId: String?
        )

        class SubsquidExtrinsic(
            val hash: String,
            val module: String,
            val call: String,
            val fee: String,
            val success: Boolean,
            val assetId: String?
        )

        class SubsquidTransfer(
            val amount: String,
            val to: String,
            val from: String,
            val fee: String?,
            val block: String?,
            val extrinsicId: String?,
            val extrinsicHash: String?,
            val success: Boolean,
            val assetId: String?
        )
    }
}

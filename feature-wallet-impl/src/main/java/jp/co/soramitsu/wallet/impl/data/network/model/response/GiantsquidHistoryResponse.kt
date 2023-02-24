package jp.co.soramitsu.wallet.impl.data.network.model.response

import java.math.BigInteger

class GiantsquidHistoryResponse(
    val transfers: List<GiantsquidTransferResponse>?,
    val rewards: List<GiantsquidReward>?,
    val bonds: List<GiantsquidBond>?,
    val slashes: List<GiantsquidSlash>?
) {
    class GiantsquidTransferResponse(
        val id: String,
        val transfer: GiantsquidTransfer
    )

    class GiantsquidAccount(
        val id: String
    )

    class GiantsquidTransfer(
        val id: String,
        val blockNumber: BigInteger,
        val timestamp: String,
        val extrinsicHash: String?,
        val from: GiantsquidAccount?,
        val to: GiantsquidAccount?,
        val amount: String,
        val success: Boolean
    )

    class GiantsquidReward(
        val id: String,
        val timestamp: String,
        val blockNumber: Int,
        val extrinsicHash: String?,
        val amount: String,
        val era: BigInteger?,
        val validatorId: String?,
        val account: GiantsquidAccount?
    )

    class GiantsquidBond(
        val id: String,
        val accountId: String,
        val amount: String,
        val blockNumber: BigInteger,
        val extrinsicHash: String?,
        val success: Boolean?,
        val timestamp: String,
        val type: String?
    )

    class GiantsquidSlash(
        val id: String,
        val accountId: String,
        val amount: String,
        val blockNumber: BigInteger,
        val era: BigInteger,
        val timestamp: String
    )
}

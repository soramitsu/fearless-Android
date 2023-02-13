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

    class GiantsquidDestination(
        val id: String
    )

    class GiantsquidTransfer(
        val id: String,
        val amount: String,
        val to: GiantsquidDestination?,
        val from: GiantsquidDestination?,
        val success: Boolean?,
        val extrinsicHash: String?,
        val timestamp: String,
        val blockNumber: BigInteger?,
        val type: String?
    )

    class GiantsquidReward(
        val amount: String,
        val era: BigInteger?,
        val accountId: String?,
        val validator: String?,
        val timestamp: String,
        val extrinsicHash: String?,
        val blockNumber: BigInteger?,
        val id: String
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

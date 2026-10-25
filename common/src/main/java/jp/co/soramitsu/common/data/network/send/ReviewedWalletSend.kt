package jp.co.soramitsu.common.data.network.send

/** Original reviewed public payment identity, retained independently of later balances and quotes. */
data class ReviewedWalletSend(
    val chainId: String,
    val networkName: String,
    val assetId: String,
    val currencyId: String?,
    val symbol: String,
    val precision: Int,
    val sender: String,
    val recipient: String,
    val amountUnits: String
)

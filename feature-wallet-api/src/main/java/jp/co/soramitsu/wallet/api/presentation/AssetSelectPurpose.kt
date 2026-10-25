package jp.co.soramitsu.wallet.api.presentation

/**
 * Why an asset picker is open, which decides what it lists. Spending pickers list what the wallet
 * can spend, hidden assets included; receiving pickers list every reviewed asset of the network,
 * held or not, so a first deposit never starts from an empty list.
 */
enum class AssetSelectPurpose {
    SEND,
    RECEIVE,
    SWAP_FROM,
    SWAP_TO,
    XCM;

    val isSpending: Boolean
        get() = this == SEND || this == SWAP_FROM || this == XCM

    companion object {
        /** A picker opened without a purpose (a bundle from an older build) is a Send picker. */
        fun fromName(name: String?): AssetSelectPurpose = entries.firstOrNull { it.name == name } ?: SEND
    }
}

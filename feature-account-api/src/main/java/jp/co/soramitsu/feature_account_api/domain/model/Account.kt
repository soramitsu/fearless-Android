package jp.co.soramitsu.feature_account_api.domain.model

private const val ADDRESS_CHARACTERS_TRUNCATE = 6

data class Account(
    val address: String,
    val name: String?,
    val publicKey: String,
    val cryptoType: CryptoType,
    val networkType: Node.NetworkType
) {
    val shortAddress: String
        get() = shortenAddress()

    private fun shortenAddress(): String {
        val address = address

        return "${address.take(ADDRESS_CHARACTERS_TRUNCATE)}...${address.takeLast(
            ADDRESS_CHARACTERS_TRUNCATE
        )}"
    }
}
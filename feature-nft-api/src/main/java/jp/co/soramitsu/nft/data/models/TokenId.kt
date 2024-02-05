package jp.co.soramitsu.nft.data.models

class TokenId(
    val tokenId: String?,
    val tokenMetadata: TokenMetadata?
) {
    companion object;

    class TokenMetadata(
        val tokenType: String?,
    ) {
        companion object;
    }
}
package jp.co.soramitsu.nft.data.models

sealed interface TokenId {

    val tokenId: String?

    class WithoutMetadata(
        override val tokenId: String?
    ): TokenId {
        companion object;
    }

    class WithMetadata(
        override val tokenId: String?,
        val tokenMetadata: TokenMetadata?
    ): TokenId {
        companion object;

        class TokenMetadata(
            val tokenType: String?,
        ) {
            companion object;
        }
    }

}
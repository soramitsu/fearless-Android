package jp.co.soramitsu.nft.data.models

sealed interface TokenId {

    val tokenId: String?

    data class WithoutMetadata(
        override val tokenId: String?
    ): TokenId {
        companion object;
    }

    data class WithMetadata(
        override val tokenId: String?,
        val tokenMetadata: TokenMetadata?
    ): TokenId {
        companion object;

        data class TokenMetadata(
            val tokenType: String?,
        ) {
            companion object;
        }
    }

}
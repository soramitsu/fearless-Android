package jp.co.soramitsu.nft.data.models.response

import jp.co.soramitsu.nft.data.models.TokenInfo

sealed interface NFTResponse {

    data class ContractMetadata(
        val address: String?,
        val contractMetadata: TokenInfo.WithMetadata.ContractMetadata?
    ): NFTResponse {
        companion object;
    }

    data class UserOwnedTokens(
        val ownedNfts: List<TokenInfo.WithoutMetadata>,
        val pageKey: String?,
        val totalCount: Int?
    ): NFTResponse {
        companion object;
    }

    data class TokensCollection(
        val nfts: List<TokenInfo.WithMetadata>,
        val nextToken: String?
    ): NFTResponse {
        companion object;
    }

}

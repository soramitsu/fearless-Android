package jp.co.soramitsu.nft.data.models.wrappers

import jp.co.soramitsu.nft.data.models.TokenInfo

sealed interface NFTResponse {

    class ContractMetadata(
        val address: String?,
        val contractMetadata: TokenInfo.WithMetadata.ContractMetadata?
    ): NFTResponse {
        companion object;
    }

    data class UserOwnedTokens(
        val tokensInfoList: List<TokenInfo.WithoutMetadata>,
        val nextPage: String?,
        val totalCount: Int?
    ): NFTResponse {
        companion object;
    }

    class TokensCollection(
        val tokenInfoList: List<TokenInfo.WithMetadata>,
        val nextPage: String?
    ): NFTResponse {
        companion object;
    }

}

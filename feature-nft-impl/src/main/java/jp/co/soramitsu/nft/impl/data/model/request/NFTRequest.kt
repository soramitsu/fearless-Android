package jp.co.soramitsu.nft.impl.data.model.request

import jp.co.soramitsu.feature_nft_impl.BuildConfig
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull

sealed interface NFTRequest {
    class ContractMetadataBatch: NFTRequest {

        companion object {
            fun requestUrl(alchemyChainId: String?): String {
                return "https://$alchemyChainId.g.alchemy.com/nft/v2/${BuildConfig.ALCHEMY_API_KEY}/getContractMetadataBatch"
            }
        }

        data class Body(
            val mediaType: MediaType = "application/json".toMediaTypeOrNull()!!,
            val contractAddresses: List<String>
        )
    }

    class UserOwnedTokens: NFTRequest {
        companion object {
            fun requestUrl(alchemyChainId: String?): String {
                return "https://$alchemyChainId.g.alchemy.com/nft/v2/${BuildConfig.ALCHEMY_API_KEY}/getNFTs"
            }
        }
    }

    class TokensCollection: NFTRequest {
        companion object {
            fun requestUrl(alchemyChainId: String?): String {
                return "https://$alchemyChainId.g.alchemy.com/nft/v2/${BuildConfig.ALCHEMY_API_KEY}/getNFTsForCollection"
            }
        }
    }

    class TokenMetadata: NFTRequest {
        companion object {
            fun requestUrl(alchemyChainId: String?): String {
                return "https://$alchemyChainId.g.alchemy.com/nft/v2/${BuildConfig.ALCHEMY_API_KEY}/getNFTMetadata"
            }
        }
    }

    class TokenOwners: NFTRequest {
        companion object {
            fun requestUrl(alchemyChainId: String?): String {
                return "https://$alchemyChainId.g.alchemy.com/nft/v2/${BuildConfig.ALCHEMY_API_KEY}/getOwnersForToken"
            }
        }
    }

}
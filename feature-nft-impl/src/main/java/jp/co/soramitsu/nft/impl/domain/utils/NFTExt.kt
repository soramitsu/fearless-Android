package jp.co.soramitsu.nft.impl.domain.utils

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.domain.models.NFT

fun NFT.convertToShareMessage(
    resourceManager: ResourceManager,
    tokenOwnerAddress: String?,
    userAddress: String?
): String {
    return buildString {
        tokenOwnerAddress?.let {
            appendLine("${resourceManager.getString(R.string.nft_owner_title)}: $it")
        }
        appendLine("${resourceManager.getString(R.string.common_network)}: $chainName")
        appendLine("${resourceManager.getString(R.string.nft_creator_title)}: $creatorAddress")
        appendLine("${resourceManager.getString(R.string.nft_collection_title)}: $collectionName")
        appendLine("${resourceManager.getString(R.string.nft_token_type_title)}: $tokenType")
        appendLine("${resourceManager.getString(R.string.nft_tokenid_title)}: $tokenId")
        userAddress?.let {
            val string = resourceManager.getString(R.string.wallet_receive_share_message).format(
                "Ethereum",
                it
            )
            appendLine(string)
        }
    }
}

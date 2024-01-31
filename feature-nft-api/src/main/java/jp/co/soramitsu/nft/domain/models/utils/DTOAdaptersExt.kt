package jp.co.soramitsu.nft.domain.models.utils

import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

fun NFTResponse.ContractMetadata.toLightNFTCollection(
    chain: Chain,
    contractAddress: String?,
    tokens: List<NFTCollection.NFT.Light>
): NFTCollection<NFTCollection.NFT.Light> = NFTCollection(
        chainId = chain.id,
        chainName = chain.name,
        collectionName = contractMetadata?.openSea?.collectionName
                ?: contractMetadata?.name ?: address.orEmpty(),
        contractAddress = contractAddress,
        description = contractMetadata?.openSea?.description,
        imageUrl = contractMetadata?.openSea?.imageUrl,
        type = contractMetadata?.tokenType,
        tokens = tokens,
        collectionSize = contractMetadata?.totalSupply?.toIntOrNull()
                ?: tokens.size
)

fun NFTResponse.TokensCollection.toFullNFTCollection(
    chain: Chain,
    contractAddress: String?,
    contractMetadata: TokenInfo.WithMetadata.ContractMetadata?
): NFTCollection<NFTCollection.NFT.Full> = NFTCollection(
        chainId = chain.id,
        chainName = chain.name,
        collectionName = contractMetadata?.openSea?.collectionName
                ?: contractMetadata?.name ?: contractAddress.orEmpty(),
        contractAddress = contractAddress,
        description = contractMetadata?.openSea?.description,
        imageUrl = contractMetadata?.openSea?.imageUrl,
        type = contractMetadata?.tokenType,
        tokens = tokenInfoList.map {
            it.toFullNFT(
                    chain = chain,
                    contractAddress = it.contract?.address.orEmpty()
            )
        },
        collectionSize = contractMetadata?.totalSupply?.toIntOrNull()
                ?: tokenInfoList.size
)

fun TokenInfo.WithMetadata.toFullNFT(chain: Chain, contractAddress: String?): NFTCollection.NFT.Full {
    val collectionName = contractMetadata?.openSea?.collectionName
            ?: contractMetadata?.name ?: contractAddress.orEmpty()

    return NFTCollection.NFT.Full(
            title = title ?: metadata?.name
            ?: collectionName.let { name -> "$name ${id?.tokenId}" },
            thumbnail = (media?.firstOrNull { it.thumbnail != null }?.thumbnail ?: metadata?.image)
                    .orEmpty(),
            description = (description ?: contractMetadata?.openSea?.description).orEmpty(),
            collectionName = collectionName,
            contractAddress = contractAddress,
            isUserOwnedToken = !balance.isNullOrEmpty() && balance.toIntOrNull() != 0,
            tokenId = id?.tokenId,
            chainName = chain.name,
            chainId = chain.id,
            tokenType = id?.tokenMetadata?.tokenType,
            creatorAddress = contractMetadata?.contractDeployer,
            date = "",
            price = "",
    )
}

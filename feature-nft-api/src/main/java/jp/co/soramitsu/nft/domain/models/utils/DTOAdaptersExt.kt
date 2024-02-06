package jp.co.soramitsu.nft.domain.models.utils

import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

fun ContractInfo.toLightNFTCollection(
    chainId: ChainId,
    chainName: String,
): NFTCollection<NFT.Light> {
    val collectionName = if (!title.isNullOrBlank())
        title
    else if (!openSea?.collectionName.isNullOrBlank())
        openSea?.collectionName.orEmpty()
    else if (!name.isNullOrBlank())
        name
    else address.orEmpty()

    val thumbnailUrl = media?.firstOrNull {
        !it.thumbnail.isNullOrBlank()
    }?.thumbnail ?: openSea?.imageUrl

    val tokens = listOfNotNull(
        NFT.Light(
            contractAddress = address,
            tokenId = tokenId,
            balance = null
        )
    )

    val userOwnedTokens = totalBalance ?: numDistinctTokensOwned

    return NFTCollection.Data(
        chainId = chainId,
        chainName = chainName,
        collectionName = collectionName,
        contractAddress = address,
        description = openSea?.description,
        imageUrl = thumbnailUrl,
        type = tokenType,
        tokens = tokens,
        balance = userOwnedTokens ?: 0,
        collectionSize = totalSupply ?: totalBalance ?: 0
    )
}


fun NFTResponse.TokensCollection.toFullNFTCollection(
    chain: Chain
): NFTCollection<NFT.Full> {
    val firstToken = tokenInfoList.firstOrNull {
        it.contract?.address != null &&
        it.contractMetadata != null
    }

    val contractAddress = firstToken?.contract?.address.orEmpty()

    val collectionName = if (!firstToken?.title.isNullOrBlank())
        firstToken?.title.orEmpty()
    else if (!firstToken?.contractMetadata?.openSea?.collectionName.isNullOrBlank())
        firstToken?.contractMetadata?.openSea?.collectionName.orEmpty()
    else if (!firstToken?.contractMetadata?.name.isNullOrBlank())
        firstToken?.contractMetadata?.name.orEmpty()
    else contractAddress

    val contractMetadata = firstToken?.contractMetadata

    return NFTCollection.Data(
        chainId = chain.id,
        chainName = chain.name,
        collectionName = collectionName,
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
        balance = tokenInfoList.size,
        collectionSize = contractMetadata?.totalSupply?.toIntOrNull()
            ?: tokenInfoList.size
    )
}

fun TokenInfo.toFullNFT(
    chain: Chain,
    contractAddress: String?
): NFT.Full {
    val collectionName = contractMetadata?.openSea?.collectionName
        ?: contractMetadata?.name ?: contractAddress.orEmpty()

    return NFT.Full(
        title = title ?: metadata?.name ?: collectionName.let { name -> "$name ${id?.tokenId}" },
        thumbnail = (media?.firstOrNull { it.thumbnail != null }?.thumbnail ?: metadata?.image).orEmpty(),
        description = (description ?: contractMetadata?.openSea?.description).orEmpty(),
        collectionName = collectionName,
        contractAddress = contractAddress,
        creatorAddress = contractMetadata?.contractDeployer,
        isUserOwnedToken = !balance.isNullOrEmpty() && balance.toIntOrNull() != 0,
        tokenId = id?.tokenId,
        chainName = chain.name,
        chainId = chain.id,
        tokenType = id?.tokenMetadata?.tokenType,
        date = "",
        price = "",
    )
}
package jp.co.soramitsu.nft.domain.models.utils

import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import java.math.BigInteger

fun ContractInfo.toLightNFTCollection(
    chainId: ChainId,
    chainName: String,
): NFTCollection<NFT.Light> {
    val contractAddress = address.orEmpty()

    val collectionName = if (!title.isNullOrBlank())
        title
    else if (!openSea?.collectionName.isNullOrBlank())
        openSea?.collectionName.orEmpty()
    else if (!name.isNullOrBlank())
        name
    else address.orEmpty()

    val description =
        openSea?.description ?: collectionName

    val thumbnailUrl = media?.firstOrNull {
        !it.thumbnail.isNullOrBlank()
    }?.thumbnail ?: openSea?.imageUrl.orEmpty()

    val tokenType = tokenType.orEmpty()

    val userOwnedTokens = totalBalance ?: numDistinctTokensOwned

    return NFTCollection.Data(
        chainId = chainId,
        chainName = chainName,
        collectionName = collectionName,
        contractAddress = contractAddress,
        description = description,
        imageUrl = thumbnailUrl,
        type = tokenType,
        tokens = emptyList(),
        balance = userOwnedTokens ?: 0,
        collectionSize = totalSupply ?: totalBalance ?: 0
    )
}


fun NFTResponse.TokensCollection.toFullNFTCollection(
    chain: Chain,
    excludeTokensWithIds: Set<String>? = null
): NFTCollection<NFT.Full> {
    val firstToken = tokenInfoList.firstOrNull {
        !it.contract?.address.isNullOrBlank() &&
        it.contractMetadata != null
    }

    val contractAddress = firstToken?.contract?.address.orEmpty()

    val collectionName =
        if (!firstToken?.contractMetadata?.openSea?.collectionName.isNullOrBlank())
            firstToken?.contractMetadata?.openSea?.collectionName.orEmpty()
        else if (!firstToken?.contractMetadata?.name.isNullOrBlank())
            firstToken?.contractMetadata?.name.orEmpty()
        else contractAddress.requireHexPrefix().drop(2).shortenAddress(3)

    val contractMetadata = firstToken?.contractMetadata

    val description =
        contractMetadata?.openSea?.description ?: collectionName

    val media = firstToken?.media?.firstOrNull {
        !it.thumbnail.isNullOrBlank() ||
        !it.gateway.isNullOrBlank()
    }

    val imageUrl = when {
        !media?.thumbnail.isNullOrBlank() -> media?.thumbnail.orEmpty()
        !media?.gateway.isNullOrBlank() -> media?.gateway.orEmpty()
        !contractMetadata?.openSea?.imageUrl.isNullOrBlank() -> contractMetadata?.openSea?.imageUrl.orEmpty()
        else -> ""
    }

    val tokenType = contractMetadata?.tokenType.orEmpty()

    val tokens = tokenInfoList.mapNotNull {
        if (it.id?.tokenId != null && excludeTokensWithIds?.contains(it.id.tokenId) == true)
            return@mapNotNull null

        it.toFullNFT(
            chain = chain
        )
    }.takeIf { it.isNotEmpty() } ?: return NFTCollection.Empty(chain.id, chain.name)

    return NFTCollection.Data(
        chainId = chain.id,
        chainName = chain.name,
        collectionName = collectionName,
        contractAddress = contractAddress,
        description = description,
        imageUrl = imageUrl,
        type = tokenType,
        tokens = tokens,
        balance = tokenInfoList.size,
        collectionSize = contractMetadata?.totalSupply?.toIntOrNull()
            ?: tokenInfoList.size
    )
}

fun TokenInfo.toFullNFT(
    chain: Chain
): NFT.Full {
    val contractAddress = contract?.address.orEmpty()

    val tokenId = id?.tokenId?.requireHexPrefix()?.drop(2)?.run {
        BigInteger(this, 16)
    } ?: BigInteger("-1")

    val collectionName =
        if (!contractMetadata?.openSea?.collectionName.isNullOrBlank())
            contractMetadata?.openSea?.collectionName.orEmpty()
        else if (!contractMetadata?.name.isNullOrBlank())
            contractMetadata?.name.orEmpty()
        else contractAddress.requireHexPrefix().drop(2).shortenAddress(3)

    val description =
        contractMetadata?.openSea?.description ?: collectionName

    val title = if (!title.isNullOrBlank())
        title
    else if (!metadata?.name.isNullOrBlank())
        metadata?.name.orEmpty()
    else {
        val tokenIdShortString = if (tokenId > BigInteger("99")) {
            tokenId.toString().take(3) + "..."
        } else {
            tokenId.toString()
        }

        "$collectionName #${tokenIdShortString}"
    }

    val media = media?.firstOrNull {
        !it.thumbnail.isNullOrBlank() ||
        !it.gateway.isNullOrBlank()
    }

    val thumbnail = when {
        !media?.thumbnail.isNullOrBlank() -> media?.thumbnail.orEmpty()
        !media?.gateway.isNullOrBlank() -> media?.gateway.orEmpty()
        !contractMetadata?.openSea?.imageUrl.isNullOrBlank() -> contractMetadata?.openSea?.imageUrl.orEmpty()
        else -> ""
    }

    val tokenType = contractMetadata?.tokenType.orEmpty()

    return NFT.Full(
        title = title,
        thumbnail = thumbnail,
        description = description,
        collectionName = collectionName,
        contractAddress = contractAddress,
        creatorAddress = contractMetadata?.contractDeployer,
        isUserOwnedToken = !balance.isNullOrEmpty() && balance.toIntOrNull() != 0,
        tokenId = tokenId,
        chainName = chain.name,
        chainId = chain.id,
        tokenType = tokenType,
        date = "",
        price = "",
    )
}
package jp.co.soramitsu.nft.domain.models.utils

import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import java.math.BigInteger

fun ContractInfo.toNFTCollection(chainId: ChainId, chainName: String): NFTCollectionResult {
    val contractAddress = address.orEmpty()

    val collectionName = if (!title.isNullOrBlank()) {
        title
    } else if (!openSea?.collectionName.isNullOrBlank()) {
        openSea?.collectionName.orEmpty()
    } else if (!name.isNullOrBlank()) {
        name
    } else {
        contractAddress.requireHexPrefix().drop(DEFAULT_HEX_PREFIX_LENGTH)
            .shortenAddress(DEFAULT_CONTRACT_ADDRESS_SHORTENED_LENGTH)
    }

    val description =
        openSea?.description ?: collectionName

    val thumbnailUrl = media?.firstOrNull {
        !it.thumbnail.isNullOrBlank()
    }?.thumbnail ?: openSea?.imageUrl.orEmpty()

    val tokenType = tokenType.orEmpty()

    val userOwnedTokens = totalBalance ?: numDistinctTokensOwned

    return NFTCollectionResult.Data(
        chainId = chainId,
        chainName = chainName,
        collectionName = collectionName,
        contractAddress = contractAddress,
        description = description,
        imageUrl = thumbnailUrl,
        type = tokenType,
        balance = userOwnedTokens ?: 0,
        collectionSize = totalSupply ?: totalBalance ?: 0
    )
}

fun NFTResponse.TokensCollection.toNFTCollectionWithTokens(
    chain: Chain,
    excludeTokensWithIds: Set<String>? = null
): NFTCollectionResult {
    val firstToken = tokenInfoList.firstOrNull {
        !it.contract?.address.isNullOrBlank() &&
        it.contractMetadata != null
    }

    val contractAddress = firstToken?.contract?.address.orEmpty()

    val collectionName =
        if (!firstToken?.contractMetadata?.openSea?.collectionName.isNullOrBlank()) {
            firstToken?.contractMetadata?.openSea?.collectionName.orEmpty()
        } else if (!firstToken?.contractMetadata?.name.isNullOrBlank()) {
            firstToken?.contractMetadata?.name.orEmpty()
        } else {
            contractAddress.requireHexPrefix().drop(DEFAULT_HEX_PREFIX_LENGTH)
                .shortenAddress(DEFAULT_CONTRACT_ADDRESS_SHORTENED_LENGTH)
        }

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
        if (it.id?.tokenId != null && excludeTokensWithIds?.contains(it.id.tokenId) == true) {
            return@mapNotNull null
        }

        it.toNFT(
            chain = chain
        )
    }.takeIf { it.isNotEmpty() } ?: return NFTCollectionResult.Empty(chain.id, chain.name)

    return NFTCollectionResult.Data(
        chainId = chain.id,
        chainName = chain.name,
        collectionName = collectionName,
        contractAddress = contractAddress,
        description = description,
        imageUrl = imageUrl,
        type = tokenType,
        balance = tokenInfoList.size,
        collectionSize = contractMetadata?.totalSupply?.toIntOrNull() ?: tokenInfoList.size
    ).run {
        NFTCollectionResult.Data.WithTokens(
            data = this,
            tokens = tokens
        )
    }
}

fun TokenInfo.toNFT(chain: Chain): NFT {
    val contractAddress = contract?.address.orEmpty()

    val tokenId = id?.tokenId?.requireHexPrefix()?.drop(2)?.run {
        BigInteger(this, DEFAULT_TOKEN_ID_RADIX)
    } ?: BigInteger("-1")

    val collectionName =
        if (!contractMetadata?.openSea?.collectionName.isNullOrBlank()) {
            contractMetadata?.openSea?.collectionName.orEmpty()
        } else if (!contractMetadata?.name.isNullOrBlank()) {
            contractMetadata?.name.orEmpty()
        } else {
            contractAddress.requireHexPrefix().drop(DEFAULT_HEX_PREFIX_LENGTH)
                .shortenAddress(DEFAULT_CONTRACT_ADDRESS_SHORTENED_LENGTH)
        }

    val description =
        contractMetadata?.openSea?.description ?: collectionName

    val title = if (!title.isNullOrBlank()) {
        title
    } else if (!metadata?.name.isNullOrBlank()) {
        metadata?.name.orEmpty()
    } else {
        val tokenIdShortString = if (tokenId > BigInteger("99")) {
            tokenId.toString().take(DEFAULT_TOKEN_ID_SHORTENED_LENGTH) + "..."
        } else {
            tokenId.toString()
        }

        "$collectionName #$tokenIdShortString"
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

    return NFT(
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

private const val DEFAULT_HEX_PREFIX_LENGTH = 2
private const val DEFAULT_CONTRACT_ADDRESS_SHORTENED_LENGTH = 3
private const val DEFAULT_TOKEN_ID_SHORTENED_LENGTH = 3
private const val DEFAULT_TOKEN_ID_RADIX = 16

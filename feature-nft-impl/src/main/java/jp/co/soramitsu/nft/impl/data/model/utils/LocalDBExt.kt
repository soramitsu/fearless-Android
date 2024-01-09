package jp.co.soramitsu.nft.impl.data.model.utils

import jp.co.soramitsu.coredb.model.NFTContractMetadataLocal
import jp.co.soramitsu.coredb.model.NFTContractMetadataResponseLocal
import jp.co.soramitsu.coredb.model.NFTOpenSeaLocal
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.response.NFTResponse
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

fun NFTContractMetadataResponseLocal.toContractMetadataResponse(): NFTResponse.ContractMetadata =
    NFTResponse.ContractMetadata(
        address = address,
        contractMetadata = contractMetadata?.toContractMetadata()
    )

fun NFTResponse.ContractMetadata.toNFTContractMetadataResponseLocal(chainId: ChainId): NFTContractMetadataResponseLocal =
    NFTContractMetadataResponseLocal(
        chainId = chainId,
        address = address.orEmpty(),
        contractMetadata = contractMetadata?.toNFTContractMetadataLocal()
    )

fun NFTContractMetadataLocal.toContractMetadata(): TokenInfo.WithMetadata.ContractMetadata =
    TokenInfo.WithMetadata.ContractMetadata(
        name = name,
        symbol = symbol,
        totalSupply = totalSupply,
        tokenType = tokenType,
        contractDeployer = contractDeployer,
        deployedBlockNumber = deployedBlockNumber,
        openSea = openSea?.toOpenSea()
    )

fun NFTOpenSeaLocal.toOpenSea(): TokenInfo.WithMetadata.ContractMetadata.OpenSea =
    TokenInfo.WithMetadata.ContractMetadata.OpenSea(
        floorPrice = floorPrice,
        collectionName = collectionName,
        safelistRequestStatus = safelistRequestStatus,
        imageUrl = imageUrl,
        description = description,
        externalUrl = externalUrl,
        twitterUsername = twitterUsername,
        lastIngestedAt = lastIngestedAt
    )

fun TokenInfo.WithMetadata.ContractMetadata.toNFTContractMetadataLocal(): NFTContractMetadataLocal =
    NFTContractMetadataLocal(
        name = name,
        symbol = symbol,
        totalSupply = totalSupply,
        tokenType = tokenType,
        contractDeployer = contractDeployer,
        deployedBlockNumber = deployedBlockNumber,
        openSea = openSea?.toNFTOpenSeaLocal()
    )

fun TokenInfo.WithMetadata.ContractMetadata.OpenSea.toNFTOpenSeaLocal(): NFTOpenSeaLocal =
    NFTOpenSeaLocal(
        floorPrice = floorPrice,
        collectionName = collectionName,
        safelistRequestStatus = safelistRequestStatus,
        imageUrl = imageUrl,
        description = description,
        externalUrl = externalUrl,
        twitterUsername = twitterUsername,
        lastIngestedAt = lastIngestedAt
    )
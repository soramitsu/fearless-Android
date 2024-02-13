package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.nft.data.NFTCollectionByContractAddressPagedResponse
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.data.pagination.PaginationEvent
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import javax.inject.Inject

class NFTCollectionsByContractMappingAdapter @Inject constructor () {

    operator fun invoke(
        nftContractsFlowFlow: () -> Flow<NFTCollectionByContractAddressPagedResponse>
    ): Flow<Pair<NFTCollectionResult, PaginationRequest>> =
        nftContractsFlowFlow.invoke().map { pagedResponse ->
            pagedResponse.mapToNFTCollectionResultWithToken() to pagedResponse.paginationRequest
        }.flowOn(Dispatchers.Default)

    private fun NFTCollectionByContractAddressPagedResponse.mapToNFTCollectionResultWithToken(): NFTCollectionResult {
        val (chainId, chainName) = chain.run { id to name }

        return result.mapCatching { paginationEvent ->
            if (paginationEvent !is PaginationEvent.PageIsLoaded) {
                return@mapCatching NFTCollectionResult.Empty(chainId, chainName)
            }

            if (paginationEvent.data.tokenInfoList.isEmpty()) {
                return@mapCatching NFTCollectionResult.Empty(chainId, chainName)
            }

            paginationEvent.data.toNFTCollectionWithTokens(chain)
        }.getOrElse { throwable ->
            NFTCollectionResult.Error(
                chainId = chainId,
                chainName = chainName,
                throwable = throwable
            )
        }
    }

    private fun NFTResponse.TokensCollection.toNFTCollectionWithTokens(chain: Chain): NFTCollectionResult {
        val firstToken = tokenInfoList.firstOrNull {
            !it.contract?.address.isNullOrBlank() && it.contractMetadata != null
        }

        val contractAddress =
            firstToken?.contract?.address.orEmpty()

        val collectionName =
            getCollectionName(firstToken?.contractMetadata, contractAddress)

        val contractMetadata =
            firstToken?.contractMetadata

        val tokens = getTokens(tokenInfoList, chain).ifEmpty {
            return NFTCollectionResult.Empty(chain.id, chain.name)
        }

        return NFTCollectionResult.Data(
            chainId = chain.id,
            chainName = chain.name,
            collectionName = collectionName,
            contractAddress = contractAddress,
            description = getDescription(contractMetadata, collectionName),
            imageUrl = getThumbnail(firstToken?.media, contractMetadata),
            type = getTokenType(contractMetadata),
            balance = tokenInfoList.size,
            collectionSize = contractMetadata?.totalSupply?.toIntOrNull() ?: tokenInfoList.size
        ).run {
            NFTCollectionResult.Data.WithTokens(
                data = this,
                tokens = tokens
            )
        }
    }

    private fun TokenInfo.toNFT(chain: Chain): NFT {
        val contractAddress = contract?.address.orEmpty()

        val tokenId = id?.tokenId?.requireHexPrefix()?.drop(2)?.run {
            BigInteger(this, DEFAULT_TOKEN_ID_RADIX)
        } ?: BigInteger("-1")

        val collectionName = getCollectionName(contractMetadata, contractAddress)

        return NFT(
            title = getTokenTitle(title, contractMetadata, collectionName, tokenId),
            thumbnail = getThumbnail(media, contractMetadata),
            description = getDescription(contractMetadata, collectionName),
            collectionName = collectionName,
            contractAddress = contractAddress,
            creatorAddress = contractMetadata?.contractDeployer,
            isUserOwnedToken = !balance.isNullOrEmpty() && balance?.toIntOrNull() != 0,
            tokenId = tokenId,
            chainName = chain.name,
            chainId = chain.id,
            tokenType = getTokenType(contractMetadata),
            date = "",
            price = "",
        )
    }

    private fun getCollectionName(
        contractMetadata: TokenInfo.ContractMetadata?,
        contractAddress: String
    ): String = when {
        !contractMetadata?.openSea?.collectionName.isNullOrBlank() ->
            contractMetadata?.openSea?.collectionName.orEmpty()

        !contractMetadata?.name.isNullOrBlank() ->
            contractMetadata?.name.orEmpty()

        else -> contractAddress.requireHexPrefix().drop(DEFAULT_HEX_PREFIX_LENGTH)
            .shortenAddress(DEFAULT_CONTRACT_ADDRESS_SHORTENED_LENGTH)
    }

    private fun getTokenTitle(
        title: String?,
        metadata: TokenInfo.ContractMetadata?,
        collectionName: String,
        tokenId: BigInteger
    ): String = when {
        !title.isNullOrBlank() -> title

        !metadata?.name.isNullOrBlank() -> metadata?.name.orEmpty()

        else -> {
            val tokenIdShortString = buildString {
                if (tokenId <= BigInteger("99")) {
                    append(tokenId)
                    return@buildString
                }

                append(tokenId.toString().take(DEFAULT_TOKEN_ID_SHORTENED_LENGTH))
                append("...")
            }

            "$collectionName #$tokenIdShortString"
        }
    }

    private fun getDescription(
        contractMetadata: TokenInfo.ContractMetadata?,
        collectionName: String
    ): String = contractMetadata?.openSea?.description ?: collectionName

    private fun getThumbnail(
        mediaList: List<TokenInfo.Media>?,
        contractMetadata: TokenInfo.ContractMetadata?
    ): String {
        val media = mediaList?.firstOrNull {
            !it.thumbnail.isNullOrBlank() || !it.gateway.isNullOrBlank()
        }

        return when {
            !media?.thumbnail.isNullOrBlank() -> media?.thumbnail.orEmpty()

            !media?.gateway.isNullOrBlank() -> media?.gateway.orEmpty()

            !contractMetadata?.openSea?.imageUrl.isNullOrBlank() ->
                contractMetadata?.openSea?.imageUrl.orEmpty()

            else -> ""
        }
    }

    private fun getTokenType(contractMetadata: TokenInfo.ContractMetadata?): String {
        return contractMetadata?.tokenType.orEmpty()
    }

    private fun getTokens(tokenInfoList: List<TokenInfo>, chain: Chain): ArrayDeque<NFT> {
        return ArrayDeque(
            tokenInfoList.mapNotNull { token ->
                if (token.id?.tokenId == null) {
                    return@mapNotNull null
                }

                token.toNFT(chain)
            }
        )
    }

    private companion object {
        const val DEFAULT_HEX_PREFIX_LENGTH = 2
        const val DEFAULT_CONTRACT_ADDRESS_SHORTENED_LENGTH = 3
        const val DEFAULT_TOKEN_ID_SHORTENED_LENGTH = 3
        const val DEFAULT_TOKEN_ID_RADIX = 16
    }

}
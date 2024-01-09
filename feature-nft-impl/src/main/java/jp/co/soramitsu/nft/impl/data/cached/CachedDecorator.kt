package jp.co.soramitsu.nft.impl.data.cached

import androidx.collection.LruCache
import jp.co.soramitsu.coredb.dao.NFTContractMetadataResponseDao
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.requests.PaginationRequest
import jp.co.soramitsu.nft.data.models.response.NFTResponse
import jp.co.soramitsu.nft.impl.data.DEFAULT_PAGE_SIZE
import jp.co.soramitsu.nft.impl.data.model.utils.toContractMetadataResponse
import jp.co.soramitsu.nft.impl.data.model.utils.toNFTContractMetadataResponseLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class CachedDecorator(
    private val nftRepository: NFTRepository,
    private val nftContractMetadataResponseDao: NFTContractMetadataResponseDao
): NFTRepository by nftRepository {

    private val lruCache: LruCache<Int, TokenInfo.WithMetadata> = LruCache(DEFAULT_PAGE_SIZE)

    override fun paginatedNFTCollectionByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>
    ): Flow<Result<Pair<Chain, NFTResponse.TokensCollection>>> {
        return nftRepository.paginatedNFTCollectionByContractAddressFlow(
            paginationRequestFlow, chainSelectionFlow, contractAddressFlow
        ).onEach { result ->
            result.onSuccess { (chain, response) ->
                for(nft in response.nfts) {
                    val tokenHash = Triple(chain.id, nft.contract?.address, nft.id?.tokenId).hashCode()
                    lruCache.put(tokenHash, nft)
                }
            }
        }
    }

    override suspend fun contractMetadataBatch(
        chain: Chain,
        contractAddresses: Set<String>
    ): List<NFTResponse.ContractMetadata> {
        val cachedResponses = nftContractMetadataResponseDao.responses(chain.id, contractAddresses)

        if (cachedResponses.isNotEmpty())
            return cachedResponses.map { it.toContractMetadataResponse() }

        return nftRepository.contractMetadataBatch(chain, contractAddresses).also { values ->
            nftContractMetadataResponseDao.insert(
                responses = values.map { it.toNFTContractMetadataResponseLocal(chain.id) }
            )
        }
    }

    override suspend fun tokenMetadata(
        chain: Chain,
        contractAddress: String,
        tokenId: String
    ): TokenInfo.WithMetadata {
        val tokenHash = Triple(chain.id, contractAddress, tokenId).hashCode()
        return lruCache[tokenHash] ?: nftRepository.tokenMetadata(
            chain, contractAddress, tokenId
        ).also { lruCache.put(tokenHash, it) }
    }

}
package jp.co.soramitsu.nft.impl.data

import java.util.concurrent.atomic.AtomicReference
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.feature_nft_impl.BuildConfig
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftInfo
import jp.co.soramitsu.nft.impl.data.model.Nft
import jp.co.soramitsu.nft.impl.data.model.NftCollection
import jp.co.soramitsu.nft.impl.data.model.PaginationRequest
import jp.co.soramitsu.nft.impl.data.remote.AlchemyNftApi
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

class NftRepository(private val alchemyNftApi: AlchemyNftApi) {

    suspend fun getNfts(chain: Chain, address: String, filters: List<String>): List<NftCollection> {
        val response = alchemyNftApi.getNfts(
            url = chain.getNFTsUrl(),
            owner = address,
            excludeFilters = filters
        )

        val groupedResponse = response.ownedNfts.groupBy { it.contract?.address }

        val collections = groupedResponse.filterKeys { !it.isNullOrEmpty() }
            .cast<Map<String, List<AlchemyNftInfo>>>()
            .map { (contractAddress, nfts) ->
                val contractMetadata = nfts.first().contractMetadata
                val collectionName =
                    contractMetadata?.openSea?.collectionName
                        ?: nfts.firstOrNull { it.contractMetadata?.name != null }?.contractMetadata?.name
                        ?: nfts.firstOrNull { it.title != null }?.title// contractMetadata?.name

                val mappedNfts = nfts.map {
                    it.toNft(collectionName, address)
                }
                val collectionImage = (contractMetadata?.openSea?.imageUrl
                    ?: contractMetadata?.media?.firstOrNull()?.raw
                    ?: mappedNfts.firstOrNull()?.thumbnail).orEmpty()

                NftCollection(
                    contractAddress = contractAddress,
                    name = collectionName ?: contractAddress,
                    image = collectionImage,
                    description = contractMetadata?.openSea?.description,
                    chainId = chain.id,
                    chainName = chain.name,
                    type = contractMetadata?.tokenType,
                    nfts = mappedNfts,
                    collectionSize = contractMetadata?.totalSupply?.toIntOrNull() ?: mappedNfts.size
                )
            }

        val standaloneNftsCollections =
            groupedResponse.filterKeys { it.isNullOrEmpty() }.values.flatten().map {
                val nft = it.toNft(collectionName = null, address)
                NftCollection(
                    contractAddress = it.contract.address,
                    name = nft.title,
                    image = nft.thumbnail,
                    description = nft.description,
                    chainId = chain.id,
                    chainName = chain.name,
                    type = null,
                    nfts = listOf(nft),
                    collectionSize = 1
                )
            }
        return collections + standaloneNftsCollections
    }

    private fun Chain.getNFTsUrl(): String {
        return "https://${alchemyNftId}.g.alchemy.com/nft/v2/${BuildConfig.ALCHEMY_API_KEY}/getNFTs"
    }

    fun nftCollectionBySlug(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        collectionSlugFlow: Flow<String>
    ): Flow<Result<NftCollection>> {
        return flow {
            val nextTokenId: AtomicReference<String?> = AtomicReference(null)

            val chainSelectionHelperFlow = chainSelectionFlow.distinctUntilChanged { old, new ->
                if (old != new)
                    nextTokenId.set(null)

                return@distinctUntilChanged false
            }

            val collectionSlugHelperFlow = collectionSlugFlow.distinctUntilChanged { old, new ->
                if (old != new) {
                    nextTokenId.set(null)
                    println("This is checkpoint: changing nextTokenId due to non equal collectionSlugs")
                }

                return@distinctUntilChanged false
            }

            combine(
                paginationRequestFlow,
                chainSelectionHelperFlow,
                collectionSlugHelperFlow
            ) { request, chain, collectionSlug ->
                val pageSize = if (request is PaginationRequest.NextPageSized)
                    request.pageSize else 100

                runCatching {
                    alchemyNftApi.getNFTCollectionByCollectionSlug(
                        requestUrl = chain.getNFTCollectionUrl(),
                        collectionSlug = collectionSlug,
                        withMetadata = true,
                        startTokenId = nextTokenId.get().orEmpty(),
                        limit = pageSize
                    )
                }.onSuccess { response ->
                    val contractMetadata = response.nfts.firstOrNull()?.contractMetadata

                    val contractAddress = contractMetadata?.address.orEmpty()

                    val collectionName = contractMetadata?.openSea?.collectionName
                        ?: response.nfts.firstOrNull { it.contractMetadata?.name != null }?.contractMetadata?.name
                        ?: response.nfts.firstOrNull { it.title != null }?.title // contractMetadata?.name

                    val mappedNfts = response.nfts.map { it.toNft(collectionName, contractAddress) }

                    val collectionImage = (contractMetadata?.openSea?.imageUrl
                        ?: contractMetadata?.media?.firstOrNull()?.raw
                        ?: mappedNfts.firstOrNull()?.thumbnail).orEmpty()

                    val collection = NftCollection(
                        contractAddress = contractAddress,
                        name = collectionName ?: contractAddress,
                        image = collectionImage,
                        description = contractMetadata?.openSea?.description,
                        chainId = chain.id,
                        chainName = chain.name,
                        type = contractMetadata?.tokenType,
                        nfts = mappedNfts,
                        collectionSize = contractMetadata?.totalSupply?.toIntOrNull()
                            ?: mappedNfts.size
                    )

                    nextTokenId.set(response.nextToken)
                    emit(Result.success(collection))
                }.onFailure {
                    emit(Result.failure(it))
                }.getOrNull()
            }.collect()
        }
    }

    private fun Chain.getNFTCollectionUrl(): String {
        return "https://${alchemyNftId}.g.alchemy.com/nft/v2/${BuildConfig.ALCHEMY_API_KEY}/getNFTsForCollection"
    }
}

fun AlchemyNftInfo.toNft(collectionName: String?, address: String): Nft {
    val nftName = title ?: metadata?.name ?: collectionName?.let { name -> "$name ${id?.tokenId}" }
    return Nft(
        title = nftName.orEmpty(),
        description = (description ?: contractMetadata?.openSea?.description).orEmpty(),
        thumbnail = (media?.firstOrNull()?.thumbnail ?: metadata?.image).orEmpty(),
        owned = if (!balance.isNullOrEmpty() && balance.toIntOrNull() != 0) address else null,
        tokenId = id?.tokenId,
    )
}
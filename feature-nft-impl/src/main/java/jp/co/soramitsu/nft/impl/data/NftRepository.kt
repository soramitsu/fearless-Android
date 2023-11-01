package jp.co.soramitsu.nft.impl.data

import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.feature_nft_impl.BuildConfig
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftInfo
import jp.co.soramitsu.nft.impl.data.model.Nft
import jp.co.soramitsu.nft.impl.data.model.NftCollection
import jp.co.soramitsu.nft.impl.data.remote.AlchemyNftApi
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId

class NftRepository(private val alchemyNftApi: AlchemyNftApi) {

    suspend fun getNfts(chain: Chain, address: String, filters: List<String>): List<NftCollection> {
        val response = alchemyNftApi.getNfts(url = chain.getUrl(), owner = address, excludeFilters = filters)

        val groupedResponse = response.ownedNfts.groupBy { it.contract?.address }

        val collections = groupedResponse.filterKeys { !it.isNullOrEmpty() }
            .cast<Map<String, List<AlchemyNftInfo>>>()
            .map { (contractAddress, nfts) ->
                val contractMetadata = nfts.first().contractMetadata
                val collectionName =
                    contractMetadata?.openSea?.collectionName ?: nfts.firstOrNull { it.contractMetadata?.name != null }?.contractMetadata?.name ?: nfts.firstOrNull { it.title != null }?.title// contractMetadata?.name

                val mappedNfts = nfts.map {
                    it.toNft(collectionName, address)
                }
                val collectionImage = (contractMetadata?.openSea?.imageUrl
                    ?: contractMetadata?.media?.firstOrNull()?.raw
                    ?: mappedNfts.firstOrNull()?.thumbnail).orEmpty()

                NftCollection(
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

    private fun Chain.getUrl(): String {
        return "https://${alchemyNftId}.g.alchemy.com/nft/v2/${BuildConfig.ALCHEMY_API_KEY}/getNFTs"
    }
}

fun AlchemyNftInfo.toNft(collectionName: String?, address: String): Nft {
    val nftName = title ?: metadata?.name ?: collectionName?.let { name -> "$name ${id?.tokenId}" }
    return Nft(
        title = nftName.orEmpty(),
        description = (description ?: contractMetadata?.openSea?.description).orEmpty(),
        thumbnail = (media?.firstOrNull()?.thumbnail ?: metadata?.image).orEmpty(),
        owned = if (!balance.isNullOrEmpty() && balance.toIntOrNull() != 0) address else null,
        tokenId = id?.tokenId
    )
}
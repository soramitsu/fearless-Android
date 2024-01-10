package jp.co.soramitsu.nft.domain

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.data.models.requests.PaginationRequest
import jp.co.soramitsu.nft.domain.models.NFTFilter
import kotlinx.coroutines.flow.Flow

interface NFTInteractor {

    suspend fun setNFTFilter(filter: NFTFilter, isApplied: Boolean)

    fun nftFiltersFlow(): Flow<Map<NFTFilter, Boolean>>

    fun userOwnedNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String?>
    ): Flow<List<NFTCollection<NFTCollection.NFT.Light>>>

    fun collectionNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        contractAddressFlow: Flow<String>
    ): Flow<Result<NFTCollection<NFTCollection.NFT.Full>>>

    suspend fun getNFTDetails(
        chainId: ChainId,
        contractAddress: String,
        tokenId: String
    ): NFTCollection.NFT.Full

}
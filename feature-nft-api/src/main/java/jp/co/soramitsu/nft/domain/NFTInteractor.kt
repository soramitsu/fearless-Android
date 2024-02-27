package jp.co.soramitsu.nft.domain

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.domain.models.NFTFilter
import kotlinx.coroutines.flow.Flow

interface NFTInteractor {

    fun setNFTFilter(filter: NFTFilter, isApplied: Boolean)

    fun nftFiltersFlow(): Flow<Map<NFTFilter, Boolean>>

    fun collectionsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String?>
    ): Flow<Sequence<NFTCollection>>

    fun tokensFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        contractAddressFlow: Flow<String>
    ): Flow<NFTCollection>

    suspend fun getNFTDetails(
        chainId: ChainId,
        contractAddress: String,
        tokenId: String
    ): Result<NFT>

    suspend fun getOwnersForNFT(token: NFT): Result<List<String>?>
}

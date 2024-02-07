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

    fun userOwnedCollectionsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String?>
    ): Flow<List<NFTCollection<NFT.Light>>>

    fun collectionNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        contractAddressFlow: Flow<String>
    ): Flow<Pair<NFTCollection<NFT.Full>, PaginationRequest>>

    suspend fun getNFTDetails(
        chainId: ChainId,
        contractAddress: String,
        tokenId: String
    ): Result<NFT.Full>

    suspend fun getOwnersForNFT(token: NFT.Full): Result<List<String>>
}

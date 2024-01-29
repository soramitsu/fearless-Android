package jp.co.soramitsu.nft.domain

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow

interface NFTInteractor {

    fun setNFTFilter(filter: NFTFilter, isApplied: Boolean)

    fun nftFiltersFlow(): Flow<Map<NFTFilter, Boolean>>

    fun userOwnedNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String?>
    ): Flow<List<Pair<Chain, Result<NFTCollection<NFTCollection.NFT.Light>>>>>

    fun collectionNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        contractAddressFlow: Flow<String>
    ): Flow<Pair<Result<NFTCollection<NFTCollection.NFT.Full>>, PaginationRequest>>

    suspend fun getNFTDetails(
        chainId: ChainId,
        contractAddress: String,
        tokenId: String
    ): Result<NFTCollection.NFT.Full>

    suspend fun getOwnersForNFT(
        token: NFTCollection.NFT.Full
    ): Result<List<String>>

}
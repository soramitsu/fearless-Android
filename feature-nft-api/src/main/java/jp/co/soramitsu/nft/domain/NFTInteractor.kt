package jp.co.soramitsu.nft.domain

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.data.models.requests.PaginationRequest
import kotlinx.coroutines.flow.Flow

interface NFTInteractor {

    fun userOwnedNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String?>,
        exclusionFiltersFlow: Flow<List<String>>
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

    suspend fun send(
        token: NFTCollection.NFT.Full,
        receiver: String
    ): Result<String>

}
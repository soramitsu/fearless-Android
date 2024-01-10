package jp.co.soramitsu.nft.data

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.nft.data.models.requests.PaginationRequest
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.response.NFTResponse
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteNFTRepository()

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CachedNFTRepository()

interface NFTRepository {

    suspend fun setNFTFilter(value: String, isApplied: Boolean)

    fun nftFiltersFlow(): Flow<Set<Pair<String, Boolean>>>

    fun paginatedUserOwnedNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<List<Chain>>,
        selectedMetaAddressFlow: Flow<MetaAccount>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<List<Pair<Chain, NFTResponse.UserOwnedTokens>>>

    fun paginatedNFTCollectionByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>
    ): Flow<Result<Pair<Chain, NFTResponse.TokensCollection>>>

    suspend fun contractMetadataBatch(
        chain: Chain,
        contractAddresses: Set<String>
    ): List<NFTResponse.ContractMetadata>

    suspend fun tokenMetadata(
        chain: Chain,
        contractAddress: String,
        tokenId: String
    ): TokenInfo.WithMetadata

}
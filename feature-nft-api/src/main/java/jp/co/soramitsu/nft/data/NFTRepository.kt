package jp.co.soramitsu.nft.data

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.pagination.PaginationEvent
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteNFTRepository()

data class UserOwnedTokensPagedResponse(
    val chain: Chain,
    val result: Result<PaginationEvent<NFTResponse.UserOwnedTokens>>,
    val paginationRequest: PaginationRequest
)

data class UserOwnedTokensByContractAddressPagedResponse(
    val chain: Chain,
    val result: Result<PaginationEvent<NFTResponse.TokensCollection>>,
    val paginationRequest: PaginationRequest
)

data class NFTCollectionByContractAddressPagedResponse(
    val chain: Chain,
    val result: Result<PaginationEvent<NFTResponse.TokensCollection>>,
    val paginationRequest: PaginationRequest
)

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CachedNFTRepository()

interface NFTRepository {

    val nftFiltersFlow: Flow<Set<Pair<String, Boolean>>>

    fun setNFTFilter(value: String, isApplied: Boolean)

    fun paginatedUserOwnedNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<List<Chain>>,
        selectedMetaAccountFlow: Flow<MetaAccount>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<List<UserOwnedTokensPagedResponse>>

    fun paginatedUserOwnedNFTsByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>,
        selectedMetaAccountFlow: Flow<MetaAccount>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<UserOwnedTokensByContractAddressPagedResponse>

    fun paginatedNFTCollectionByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>
    ): Flow<NFTCollectionByContractAddressPagedResponse>

    suspend fun contractMetadataBatch(
        chain: Chain,
        contractAddresses: Set<String>
    ): List<NFTResponse.ContractMetadata>

    suspend fun tokenMetadata(
        chain: Chain,
        contractAddress: String,
        tokenId: String
    ): Result<TokenInfo.WithMetadata>

    suspend fun tokenOwners(
        chain: Chain,
        contractAddress: String,
        tokenId: String
    ): Result<NFTResponse.TokenOwners>

}
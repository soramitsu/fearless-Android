package jp.co.soramitsu.nft.data

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.data.pagination.PagedResponse
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow

interface NFTRepository {

    val nftFiltersFlow: Flow<Set<String>>

    fun setNFTFilter(value: String, excludeFromSearchQuery: Boolean)

    fun paginatedUserOwnedContractsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<List<Chain>>,
        selectedMetaAccountFlow: Flow<MetaAccount>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<List<PagedResponse<ContractInfo>>>

    fun paginatedUserOwnedNFTsByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>,
        selectedMetaAccountFlow: Flow<MetaAccount>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<PagedResponse<TokenInfo>>

    fun paginatedNFTCollectionByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>
    ): Flow<PagedResponse<TokenInfo>>

    suspend fun tokenMetadata(
        chain: Chain,
        contractAddress: String,
        tokenId: String
    ): Result<TokenInfo>

    suspend fun tokenOwners(
        chain: Chain,
        contractAddress: String,
        tokenId: String
    ): Result<NFTResponse.TokenOwners>
}

package jp.co.soramitsu.nft.impl.data

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.utils.concurrentRequestFlow
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.requests.PaginationRequest
import jp.co.soramitsu.nft.data.models.response.NFTResponse
import jp.co.soramitsu.nft.impl.data.model.request.NFTRequest
import jp.co.soramitsu.nft.impl.data.remote.AlchemyNftApi
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformLatest
import java.util.concurrent.atomic.AtomicReference

internal const val DEFAULT_PAGE_SIZE = 100

class NFTRepositoryImpl(
    private val alchemyNftApi: AlchemyNftApi
): NFTRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun paginatedUserOwnedNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<List<Chain>>,
        selectedMetaAddressFlow: Flow<MetaAccount>,
        exclusionFiltersFlow: Flow<List<String>>
    ): Flow<List<Pair<Chain, NFTResponse.UserOwnedTokens>>> {
        class LocalHolder(
            val paginationRequest: PaginationRequest,
            val chains: List<Chain>,
            val metaAccount: MetaAccount,
            val exclusionFilters: List<String>
        )

        return flow {
            val nextTokenId: AtomicReference<String?> = AtomicReference(null)

            val chainSelectionHelperFlow = chainSelectionFlow.distinctUntilChanged { old, new ->
                if (old != new)
                    nextTokenId.set(null)

                return@distinctUntilChanged false
            }

            val exclusionFilterHelperFlow = exclusionFiltersFlow.distinctUntilChanged { old, new ->
                if (old != new)
                    nextTokenId.set(null)

                return@distinctUntilChanged false
            }

            combine(
                paginationRequestFlow,
                chainSelectionHelperFlow,
                selectedMetaAddressFlow,
                exclusionFilterHelperFlow
            ) { request, chains, metaAccount, filters ->
                return@combine LocalHolder(
                    paginationRequest = request,
                    chains = chains,
                    metaAccount = metaAccount,
                    exclusionFilters = filters
                )
            }.transformLatest { holder ->
                val pageSize = if (holder.paginationRequest is PaginationRequest.NextPageSized)
                    holder.paginationRequest.pageSize else DEFAULT_PAGE_SIZE

                val result = concurrentNFTs(
                    chains = holder.chains,
                    pageSize = pageSize,
                    metaAccount = holder.metaAccount,
                    exclusionFilters = holder.exclusionFilters
                ).toList()

                emit(result)
            }.collect(this)
        }
    }

    private fun concurrentNFTs(
        chains: List<Chain>,
        pageSize: Int,
        metaAccount: MetaAccount,
        exclusionFilters: List<String>
    ): Flow<Pair<Chain, NFTResponse.UserOwnedTokens>> {
        return chains.concurrentRequestFlow { chain ->
            runCatching {
                val ownerAddress = metaAccount.address(chain) ?: error(
                    """
                        Owner is not supported for chain with id: ${chain.id}.
                    """.trimIndent()
                )

                alchemyNftApi.getUserOwnedNFTs(
                    url = NFTRequest.UserOwnedTokens.requestUrl(chain.alchemyNftId),
                    owner = ownerAddress,
                    withMetadata = false,
                    pageSize = pageSize,
                    excludeFilters = exclusionFilters
                )
            }.onSuccess { response ->
                emit(chain to response)
            }.getOrNull()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun paginatedNFTCollectionByContractAddressFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>
    ): Flow<Result<Pair<Chain, NFTResponse.TokensCollection>>> {
        return flow {
            val nextTokenId: AtomicReference<String?> = AtomicReference(null)

            val chainSelectionHelperFlow = chainSelectionFlow.distinctUntilChanged { old, new ->
                if (old != new)
                    nextTokenId.set(null)

                return@distinctUntilChanged false
            }

            val contractAddressHelperFlow = contractAddressFlow.distinctUntilChanged { old, new ->
                if (old != new)
                    nextTokenId.set(null)

                return@distinctUntilChanged false
            }

            combine(
                paginationRequestFlow,
                chainSelectionHelperFlow,
                contractAddressHelperFlow
            ) { request, chain, contractAddress ->
                return@combine Triple(request, chain, contractAddress)
            }.transformLatest { (request, chain, contractAddress) ->
                val pageSize = if (request is PaginationRequest.NextPageSized)
                    request.pageSize else DEFAULT_PAGE_SIZE

                runCatching {
                    alchemyNftApi.getNFTCollectionByContactAddress(
                        requestUrl = NFTRequest.TokensCollection.requestUrl(chain.alchemyNftId),
                        contractAddress = contractAddress,
                        withMetadata = true,
                        startTokenId = nextTokenId.get().orEmpty(),
                        limit = pageSize
                    )
                }.onSuccess { response ->
                    nextTokenId.set(response.nextToken)
                    emit(Result.success(chain to response))
                }.onFailure {
                    emit(Result.failure<Pair<Chain, NFTResponse.TokensCollection>>(it))
                }.getOrNull()
            }.collect(this)
        }
    }

    override suspend fun contractMetadataBatch(
        chain: Chain,
        contractAddresses: Set<String>
    ): List<NFTResponse.ContractMetadata> {
        return alchemyNftApi.getNFTContractMetadataBatch(
            requestUrl = NFTRequest.ContractMetadata.requestUrl(chain.alchemyNftId),
            body = NFTRequest.ContractMetadata.Body(contractAddresses = contractAddresses.toList())
        )
    }

    override suspend fun tokenMetadata(
        chain: Chain,
        contractAddress: String,
        tokenId: String
    ): TokenInfo.WithMetadata {
        return alchemyNftApi.getNFTMetadata(
            requestUrl = NFTRequest.TokenMetadata.requestUrl(chain.alchemyNftId),
            contractAddress = contractAddress,
            tokenId = tokenId
        )
    }

}
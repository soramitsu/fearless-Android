package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.utils.concurrentRequestFlow
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.UserOwnedTokensByContractAddressPagedResponse
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.pagination.PaginationEvent
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.nft.domain.models.utils.toFullNFT
import jp.co.soramitsu.nft.domain.models.utils.toFullNFTCollection
import jp.co.soramitsu.nft.domain.models.utils.toLightNFTCollection
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class NFTInteractorImpl(
    private val nftRepository: NFTRepository,
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository
): NFTInteractor {

    override fun setNFTFilter(filter: NFTFilter, isApplied: Boolean) {
        nftRepository.setNFTFilter(filter.name, !isApplied)
    }

    override fun nftFiltersFlow(): Flow<Map<NFTFilter, Boolean>> {
        return nftRepository.nftFiltersFlow.map { allCurrentlyAppliedFilters ->
            NFTFilter.values().associateWith { filter ->
                filter.name !in allCurrentlyAppliedFilters
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun userOwnedNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String?>
    ): Flow<List<NFTCollection<NFT.Light>>> {
        val chainsHelperFlow = chainsRepository.chainsFlow().map { chains ->
            chains.filter { it.supportNft && !it.alchemyNftId.isNullOrEmpty() }
        }.combine(chainSelectionFlow) { chains, chainSelection ->
            if (chainSelection.isNullOrEmpty()) chains
            else chains.filter { it.id == chainSelection }
        }

        val exclusionFiltersHelperFlow =
            nftRepository.nftFiltersFlow.map { filters ->
                filters.map { it.uppercase() }
            }

        return nftRepository.paginatedUserOwnedNFTsFlow(
            paginationRequestFlow = paginationRequestFlow,
            chainSelectionFlow = chainsHelperFlow,
            selectedMetaAccountFlow = accountRepository.selectedMetaAccountFlow(),
            exclusionFiltersFlow = exclusionFiltersHelperFlow
        ).transformLatest { responses ->
            responses.concurrentRequestFlow { pagedResponse ->
                val (chainId, chainName) = pagedResponse.chain.run { id to name }

                val result = runCatching {
                    val paginationEvent = pagedResponse.result.getOrThrow()

                    if (paginationEvent !is PaginationEvent.PageIsLoaded)
                        return@runCatching listOf(NFTCollection.Empty(chainId, chainName))

                    paginationEvent.data.contracts.map {
                        it.toLightNFTCollection(
                            chainId,
                            chainName
                        )
                    }

                }.getOrElse { listOf(NFTCollection.Error(chainId, chainName, it)) }

                emit(result)
            }.toList().also { collectionListOfAllChains ->
                emit(collectionListOfAllChains.flatten())
            }
        }.flowOn(Dispatchers.Default)
    }

    override fun collectionNFTsFlow(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        contractAddressFlow: Flow<String>
    ): Flow<Pair<NFTCollection<NFT.Full>, PaginationRequest>> {
        val chainSelectionHelperFlow =
            chainsRepository.chainsFlow().map { chains ->
                chains.filter { it.supportNft && !it.alchemyNftId.isNullOrEmpty() }
            }.combine(chainSelectionFlow) { chains, chainSelection ->
                chains.find { it.id == chainSelection } ?: error(
                    """
                        Chain with id - $chainSelection - is either not supported 
                        or does not support NFT operations.
                    """.trimIndent()
                )
            }

        val exclusionFiltersHelperFlow =
            nftRepository.nftFiltersFlow.map { filters ->
                filters.map { it.uppercase() }
            }

        return channelFlow {
            val UserOwnedNFTsStartedLoading = 0
            val AvailableNFTsStartedLoading = 1

            val nonBlockingSemaphore = AtomicInteger(UserOwnedNFTsStartedLoading)

            val mutableSharedFlow = MutableSharedFlow<Unit>(
                replay = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )

            fun <T> Flow<T>.withNonBlockingLock(unlockOn: Int) =
                onStart {
                    mutableSharedFlow.tryEmit(Unit)
                }.combine(mutableSharedFlow){ value, _ ->
                    return@combine value
                }.filter {
                    nonBlockingSemaphore.get() == unlockOn
                }

            nftRepository.paginatedUserOwnedNFTsByContractAddressFlow(
                paginationRequestFlow = paginationRequestFlow.withNonBlockingLock(UserOwnedNFTsStartedLoading),
                chainSelectionFlow = chainSelectionHelperFlow,
                contractAddressFlow = contractAddressFlow,
                selectedMetaAccountFlow = accountRepository.selectedMetaAccountFlow(),
                exclusionFiltersFlow = exclusionFiltersHelperFlow
            ).onEach { pagedResponse ->
                val (chainId, chainName) = pagedResponse.chain.run { id to name }

                val result = runCatching {
                    val paginationEvent = pagedResponse.result.getOrThrow()

                    if (paginationEvent !is PaginationEvent.PageIsLoaded) {

                        if (paginationEvent is PaginationEvent.AllNextPagesLoaded) {
                            nonBlockingSemaphore.set(AvailableNFTsStartedLoading)
                            mutableSharedFlow.tryEmit(Unit)
                        }

                        return@runCatching NFTCollection.Empty(chainId, chainName)
                    }

                    paginationEvent.toFullNFTCollection(pagedResponse.chain)
                }.getOrElse { NFTCollection.Error(chainId, chainName, it) }

                send(Pair(result, pagedResponse.paginationRequest))
            }.launchIn(this)

            nftRepository.paginatedNFTCollectionByContractAddressFlow(
                paginationRequestFlow = paginationRequestFlow.withNonBlockingLock(AvailableNFTsStartedLoading),
                chainSelectionFlow = chainSelectionHelperFlow,
                contractAddressFlow = contractAddressFlow
            ).onEach { pagedResponse ->
                val (chainId, chainName) = pagedResponse.chain.run { id to name }

                val result = runCatching {
                    val paginationEvent = pagedResponse.result.getOrThrow()

                    if (paginationEvent !is PaginationEvent.PageIsLoaded) {

                        if (paginationEvent is PaginationEvent.AllPreviousPagesLoaded){
                            nonBlockingSemaphore.set(UserOwnedNFTsStartedLoading)
                            mutableSharedFlow.tryEmit(Unit)
                        }

                        return@runCatching NFTCollection.Empty(chainId, chainName)
                    }

                    paginationEvent.toFullNFTCollection(pagedResponse.chain)
                }.getOrElse { NFTCollection.Error(chainId, chainName, it) }

                send(Pair(result, pagedResponse.paginationRequest))
            }.launchIn(this)
        }.flowOn(Dispatchers.Default)
    }

    private fun PaginationEvent.PageIsLoaded<NFTResponse.TokensCollection>.toFullNFTCollection(
        chain: Chain
    ): NFTCollection<NFT.Full> {
        val contractMetadata = data.tokenInfoList.firstOrNull {
            it.contractMetadata != null
        }?.contractMetadata

        val contractAddress = data.tokenInfoList.firstOrNull {
            it.contract?.address != null
        }?.contract?.address.orEmpty()

        return data.toFullNFTCollection(
            chain = chain,
            contractAddress = contractAddress,
            contractMetadata = contractMetadata
        )
    }

    override suspend fun getNFTDetails(
        chainId: ChainId,
        contractAddress: String,
        tokenId: String
    ): Result<NFT.Full> {
        val chain = chainsRepository.getChain(chainId)

        return nftRepository.tokenMetadata(chain, contractAddress, tokenId).map { result ->
            result.toFullNFT(
                chain = chain,
                contractAddress = contractAddress
            )
        }
    }

    override suspend fun getOwnersForNFT(token: NFT.Full): Result<List<String>> {
        val chain = chainsRepository.getChain(token.chainId)

        return runCatching {
            val contractAddress = token.contractAddress ?: error(
                """
                    TokenId supplied is null.
                """.trimIndent()
            )

            val tokenId = token.tokenId ?: error(
                """
                    TokenId supplied is null.
                """.trimIndent()
            )

            return@runCatching nftRepository.tokenOwners(
                chain = chain,
                contractAddress = contractAddress,
                tokenId = tokenId
            ).getOrThrow().ownersList
        }
    }
}
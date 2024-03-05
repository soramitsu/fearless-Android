package jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.pagination.PageBackStack
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TokensFetchingUseCase(
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository,
    private val nftRepository: NFTRepository,
    private val requestSwitchingMediator: RequestSwitchingMediator,
    private val tokensMappingAdapter: TokensMappingAdapter,
    private val tokensTrimmingMediator: TokensTrimmingMediator
) {

    operator fun invoke(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String>,
        contractAddressFlow: Flow<String>
    ): Flow<NFTCollection> {
        val chainSelectionHelperFlow = chainsRepository.chainsFlow()
            .map { chains -> chains.filter { it.supportNft && !it.alchemyNftId.isNullOrEmpty() } }
            .combine(chainSelectionFlow) { chains, chainSelection ->
                chains.find { it.id == chainSelection } ?: error(
                    """
                        Chain with id - $chainSelection - is either not supported 
                        or does not support NFT operations.
                    """.trimIndent()
                )
            }

        val exclusionFiltersHelperFlow = nftRepository.nftFiltersFlow
            .map { filters -> filters.map { it.uppercase() } }

        return channelFlow {
            fun <T> Flow<T>.withReloadingSideEffect() = distinctUntilChanged()
                .onEach { send(NFTCollection.Reloading) }

            launch {
                tokensTrimmingMediator {
                    requestSwitchingMediator(
                        requestFlow = paginationRequestFlow,
                        createUserOwnedTokensNode(
                            chainSelectionFlow = chainSelectionHelperFlow.withReloadingSideEffect(),
                            contractAddressFlow = contractAddressFlow.withReloadingSideEffect(),
                            selectedMetaAccountFlow = accountRepository.selectedMetaAccountFlow()
                                .withReloadingSideEffect(),
                            exclusionFiltersFlow = exclusionFiltersHelperFlow.withReloadingSideEffect()
                        ),
                        createAvailableTokensNode(
                            chainSelectionFlow = chainSelectionHelperFlow.withReloadingSideEffect(),
                            contractAddressFlow = contractAddressFlow.withReloadingSideEffect()
                        )
                    )
                }.collect {
                    send(it)
                }
            }
        }
    }

    private fun createUserOwnedTokensNode(
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>,
        selectedMetaAccountFlow: Flow<MetaAccount>,
        exclusionFiltersFlow: Flow<List<String>>
    ): RequestSwitchingMediator.Node<NFTCollection.Loaded> {
        return RequestSwitchingMediator.Node(
            flag = FLAG_USER_OWNED_TOKENS_FLOW
        ) { alteredPaginationRequestFlow, switchFlowHandle ->
            tokensMappingAdapter {
                nftRepository.paginatedUserOwnedNFTsByContractAddressFlow(
                    paginationRequestFlow = alteredPaginationRequestFlow,
                    chainSelectionFlow = chainSelectionFlow,
                    contractAddressFlow = contractAddressFlow,
                    selectedMetaAccountFlow = selectedMetaAccountFlow,
                    exclusionFiltersFlow = exclusionFiltersFlow
                ).mapNotNull { pagedResponse ->
                    if (pagedResponse.result.getOrNull() is PageBackStack.PageResult.NoNextPages) {
                        switchFlowHandle.switchToFlowWithFlag(FLAG_ALL_CONTRACT_TOKENS_FLOW)
                        return@mapNotNull null
                    }

                    return@mapNotNull pagedResponse
                }
            }
        }
    }

    private fun createAvailableTokensNode(
        chainSelectionFlow: Flow<Chain>,
        contractAddressFlow: Flow<String>
    ): RequestSwitchingMediator.Node<NFTCollection.Loaded> {
        return RequestSwitchingMediator.Node(
            flag = FLAG_ALL_CONTRACT_TOKENS_FLOW
        ) { alteredPaginationRequestFlow, switchFlowHandle ->
            tokensMappingAdapter {
                nftRepository.paginatedNFTCollectionByContractAddressFlow(
                    paginationRequestFlow = alteredPaginationRequestFlow,
                    chainSelectionFlow = chainSelectionFlow,
                    contractAddressFlow = contractAddressFlow
                ).mapNotNull { pagedResponse ->
                    if (pagedResponse.result.getOrNull() is PageBackStack.PageResult.NoPrevPages) {
                        switchFlowHandle.switchToFlowWithFlag(FLAG_USER_OWNED_TOKENS_FLOW)
                        return@mapNotNull null
                    }

                    return@mapNotNull pagedResponse
                }
            }
        }
    }

    private companion object {
        const val FLAG_USER_OWNED_TOKENS_FLOW = 0
        const val FLAG_ALL_CONTRACT_TOKENS_FLOW = 1
    }
}

package jp.co.soramitsu.nft.impl.domain.usecase.collections

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.alchemyNftId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CollectionsFetchingUseCase(
    private val accountRepository: AccountRepository,
    private val chainsRepository: ChainsRepository,
    private val nftRepository: NFTRepository,
    private val collectionsMappingAdapter: CollectionsMappingAdapter
) {

    operator fun invoke(
        paginationRequestFlow: Flow<PaginationRequest>,
        chainSelectionFlow: Flow<String?>
    ): Flow<Sequence<NFTCollection>> {
        val chainsHelperFlow = chainsRepository.chainsFlow().map { chains ->
            chains.filter { it.supportNft && !it.alchemyNftId.isNullOrEmpty() }
        }.combine(chainSelectionFlow) { chains, chainSelection ->
            if (chainSelection.isNullOrEmpty()) {
                chains
            } else {
                chains.filter { it.id == chainSelection }
            }
        }

        val exclusionFiltersHelperFlow =
            nftRepository.nftFiltersFlow.map { filters ->
                filters.map { it.uppercase() }
            }

        return channelFlow {
            fun <T> Flow<T>.withReloadingSideEffect() = distinctUntilChanged()
                .onEach { send(sequenceOf(NFTCollection.Reloading)) }

            launch {
                collectionsMappingAdapter {
                    nftRepository.paginatedUserOwnedContractsFlow(
                        paginationRequestFlow = paginationRequestFlow,
                        chainSelectionFlow = chainsHelperFlow.withReloadingSideEffect(),
                        selectedMetaAccountFlow = accountRepository.selectedMetaAccountFlow().withReloadingSideEffect(),
                        exclusionFiltersFlow = exclusionFiltersHelperFlow.withReloadingSideEffect()
                    )
                }.collect {
                    send(it)
                }
            }
        }
    }
}

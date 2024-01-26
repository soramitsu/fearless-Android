package jp.co.soramitsu.nft.impl.presentation.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest

@HiltViewModel
class NftCollectionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val nftInteractor: NFTInteractor
) : BaseViewModel(), NftCollectionScreenInterface {

    companion object {
        const val COLLECTION_CHAIN_ID = "selectedChainId"
        const val COLLECTION_CONTRACT_ADDRESS_KEY = "contractAddress"
    }

    private val selectedChainId = savedStateHandle.get<String>(COLLECTION_CHAIN_ID)
        ?: throw IllegalStateException("Can't find $COLLECTION_CHAIN_ID in arguments")

    private val contractAddress = savedStateHandle.get<String>(COLLECTION_CONTRACT_ADDRESS_KEY)
        ?: throw IllegalStateException("Can't find $COLLECTION_CONTRACT_ADDRESS_KEY in arguments")

    private val mutablePaginationRequestFlow = MutableSharedFlow<PaginationRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val state = createCollectionsNFTsFlow()
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(10_000),
            replay = 1
        )

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun createCollectionsNFTsFlow(): Flow<NftCollectionScreenState> {
        val paginationRequestHelperFlow = mutablePaginationRequestFlow.onStart {
            emit(PaginationRequest.Start)
        }.debounce(10_000)

        return nftInteractor.collectionNFTsFlow(
            paginationRequestFlow = paginationRequestHelperFlow,
            chainSelectionFlow = flow { emit(selectedChainId) },
            contractAddressFlow = flow { emit(contractAddress) },
        ).transformLatest { result ->
            result.fold(
                onSuccess = { collection ->
                    println("This is checkpoint: NftCollectionViewModel.collectionNFTsFlow.Result.Success - ${collection.tokens.firstOrNull()?.tokenId}")
                    NftCollectionScreenState(
                        collectionName = collection.collectionName,
                        collectionImageUrl = collection.imageUrl,
                        collectionDescription = collection.description,
                        myNFTs = collection.tokens.map {
                            NftItem(
                                thumbnailUrl = it.thumbnail,
                                name = it.title.orEmpty(),
                                description = it.description,
                                id = it.hashCode()
                            )
                        },
                        availableNFTs = emptyList()
                    ).run { emit(this) }
                },
                onFailure = {
                    throw it
                }
            )
        }
    }

    override fun close() {
    }

    override fun onItemClick(item: NftItem) {
    }

    override fun onSendClick(item: NftItem) {
    }

    override fun onShareClick(item: NftItem) {
    }

    override fun onLoadPreviousPage() {
        mutablePaginationRequestFlow.tryEmit(PaginationRequest.Prev.Page)
    }

    override fun onLoadNextPage() {
        mutablePaginationRequestFlow.tryEmit(PaginationRequest.Next.Page)
    }
}
package jp.co.soramitsu.nft.impl.presentation.collection

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.distinctUntilChangedOrDebounce
import jp.co.soramitsu.common.utils.zipWithPrevious
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.presentation.NftNavigator
import jp.co.soramitsu.nft.impl.presentation.NftNavigator.NFT_DETAILS_ROUTE_ROOT
import jp.co.soramitsu.nft.impl.presentation.collection.models.NFTsScreenView
import jp.co.soramitsu.nft.impl.presentation.collection.utils.toScreenViewStableList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

@HiltViewModel
class NftCollectionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val nftInteractor: NFTInteractor
) : BaseViewModel() {

    private val selectedChainId = requireNotNull(savedStateHandle.get<String>(COLLECTION_CHAIN_ID)) {
        "Can't find $COLLECTION_CHAIN_ID in arguments"
    }

    private val contractAddress = requireNotNull(savedStateHandle.get<String>(COLLECTION_CONTRACT_ADDRESS_KEY)) {
        "Can't find $COLLECTION_CONTRACT_ADDRESS_KEY in arguments"
    }

    private val mutablePaginationRequestFlow = MutableSharedFlow<PaginationRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val pageScrollingCallback = object : PageScrollingCallback {
        override fun onAllPrevPagesScrolled() {
            mutablePaginationRequestFlow.tryEmit(PaginationRequest.Prev.Page)
        }

        override fun onAllNextPagesScrolled() {
            mutablePaginationRequestFlow.tryEmit(PaginationRequest.Next.Page)
        }
    }

    private val mutableToolbarState = MutableStateFlow<LoadingState<ToolbarViewState>>(LoadingState.Loading())
    val toolbarState: StateFlow<LoadingState<ToolbarViewState>> = mutableToolbarState

    val state = createCollectionsNFTsFlow().shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(10_000),
        replay = 1
    )

    private fun createCollectionsNFTsFlow(): Flow<SnapshotStateList<NFTsScreenView>> {
        val paginationRequestHelperFlow = mutablePaginationRequestFlow
            .distinctUntilChangedOrDebounce(debounceTimeout = 10_000) { prevValue, currentValue ->
                if (prevValue == null) {
                    return@distinctUntilChangedOrDebounce false
                }

                return@distinctUntilChangedOrDebounce prevValue::class == currentValue::class
            }.onStart { emit(PaginationRequest.Start) }

        return nftInteractor.collectionNFTsFlow(
            paginationRequestFlow = paginationRequestHelperFlow,
            chainSelectionFlow = flow { emit(selectedChainId) },
            contractAddressFlow = flow { emit(contractAddress) },
        ).zipWithPrevious().transform { (prevValue, currentValue) ->
            val (currentResult, currentPaginationRequest) = currentValue
            val (prevResult, prevPaginationRequest) = prevValue ?: Pair(null, null)

            currentResult.fold(
                onSuccess = { collection ->
                    LoadingState.Loaded(
                        ToolbarViewState(
                            title = collection.collectionName,
                            navigationIcon = null,
                            MenuIconItem(
                                icon = R.drawable.ic_cross_24,
                                onClick = ::onCloseClick
                            )
                        )
                    ).apply { mutableToolbarState.value = this }

                    mergeUserOwnedAndAvailableNFTCollections(
                        currentValue = collection to currentPaginationRequest,
                        prevValue = prevResult?.getOrNull() to prevPaginationRequest
                    ).run { emit(this) }
                },
                onFailure = { throw it }
            )
        }
    }

    private fun mergeUserOwnedAndAvailableNFTCollections(
        currentValue: Pair<NFTCollection<NFTCollection.NFT.Full>, PaginationRequest>,
        prevValue: Pair<NFTCollection<NFTCollection.NFT.Full>?, PaginationRequest?>? = null,
    ): SnapshotStateList<NFTsScreenView> {
        val (currentCollection, currentPaginationRequest) = currentValue
        val (prevCollection, _) = prevValue ?: Pair(null, null)

        val isCurrentCollectionUserOwned = currentCollection.tokens.firstOrNull()?.isUserOwnedToken == true
        val isPrevCollectionUserOwned = prevCollection?.tokens?.firstOrNull()?.isUserOwnedToken == true

        return when {
            !isPrevCollectionUserOwned &&
            isCurrentCollectionUserOwned &&
            currentPaginationRequest is PaginationRequest.Prev -> {
                val currentCollectionViewsList =
                    currentCollection.toScreenViewStableList(::onItemClick)

                val prevCollectionsViewsList =
                    prevCollection?.toScreenViewStableList(::onItemClick) ?: SnapshotStateList()

                currentCollectionViewsList.apply {
                    addAll(prevCollectionsViewsList)
                }
            }

            isPrevCollectionUserOwned &&
            !isCurrentCollectionUserOwned &&
            currentPaginationRequest is PaginationRequest.Next -> {
                val currentCollectionViewsList =
                    currentCollection.toScreenViewStableList(::onItemClick)

                val prevCollectionsViewsList =
                    prevCollection?.toScreenViewStableList(::onItemClick) ?: SnapshotStateList()

                prevCollectionsViewsList.apply {
                    addAll(currentCollectionViewsList)
                }
            }

            else -> currentCollection.toScreenViewStableList(::onItemClick)
        }
    }

    @Suppress("OptionalUnit")
    private fun onCloseClick() = Unit

    private fun onItemClick(token: NFTCollection.NFT.Full) {
        NftNavigator.navigateTo("$NFT_DETAILS_ROUTE_ROOT/${token.contractAddress}/${token.chainId}/${token.tokenId}")
    }

    companion object {
        const val COLLECTION_CHAIN_ID = "selectedChainId"
        const val COLLECTION_CONTRACT_ADDRESS_KEY = "contractAddress"
    }
}

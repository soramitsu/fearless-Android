package jp.co.soramitsu.nft.impl.presentation.collection

import androidx.compose.runtime.snapshots.SnapshotStateList
import javax.inject.Inject
import jp.co.soramitsu.common.utils.zipWithPrevious
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.impl.navigation.Destination
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.presentation.collection.models.NFTsScreenView
import jp.co.soramitsu.nft.impl.presentation.collection.utils.toScreenViewStableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import java.util.concurrent.atomic.AtomicBoolean

class CollectionNFTsPresenter @Inject constructor(
    private val nftInteractor: NFTInteractor,
    private val internalNFTRouter: InternalNFTRouter
) {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val screenArgsFlow = internalNFTRouter.destinationsFlow
        .filterIsInstance<Destination.NestedNavGraphRoute.CollectionNFTsScreen>()
        .shareIn(coroutineScope, SharingStarted.Eagerly, 1)

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

    @OptIn(FlowPreview::class)
    fun createCollectionsNFTsFlow(): Flow<SnapshotStateList<NFTsScreenView>> {
        return channelFlow {
            val isLoadingCompleted = AtomicBoolean(true)

            val paginationRequestHelperFlow = mutablePaginationRequestFlow
                .onStart {
                    emit(PaginationRequest.Start)
                }.sample(4_500).onEach {
                    // Pagination Request Flow can quite many requests in a second, from which we need only one
                    // so we TRY to set isLoadingCompleted, if we don't succeed then
                    // we are already in process of loading
                    isLoadingCompleted.compareAndSet(true, false)
                }.shareIn(this, SharingStarted.Eagerly, 1)

            nftInteractor.collectionNFTsFlow(
                paginationRequestFlow = paginationRequestHelperFlow,
                chainSelectionFlow = screenArgsFlow.distinctUntilChanged().map { it.chainId },
                contractAddressFlow = screenArgsFlow.distinctUntilChanged().map { it.contractAddress },
            ).onEach { (collection, _) ->
                // each new element indicated that loading has been completed
                isLoadingCompleted.set(true)

                if (collection is NFTCollection.Error)
                    internalNFTRouter.openErrorsScreen(collection.throwable.message ?: "Failed to load NFTs")
            }.zipWithPrevious().transform { (prevValue, currentValue) ->
                mergeUserOwnedAndAvailableNFTCollections(
                    currentValue = currentValue,
                    prevValue = prevValue
                ).also { emit(it) }
            }.combine(paginationRequestHelperFlow) { views, paginationRequest ->
                if (!isLoadingCompleted.get()) {
                    if (paginationRequest is PaginationRequest.Prev) {
                        val screenHeaderIndex = views.indexOfFirst { it is NFTsScreenView.SectionHeader }
                        if (screenHeaderIndex == -1) {
                            views.addFirst(NFTsScreenView.LoadingIndication)
                        } else {
                            views.add(screenHeaderIndex + 1, NFTsScreenView.LoadingIndication)
                        }
                    } else {
                        views.addLast(NFTsScreenView.LoadingIndication)
                    }
                }

                return@combine views
            }.onEach {
                // Transforming arrayDeque to STABLE snapshotStateList outside of UI for optimization
                val viewsStateList = SnapshotStateList<NFTsScreenView>().apply { addAll(it) }
                send(viewsStateList)
            }.launchIn(this)
        }
    }

    private fun mergeUserOwnedAndAvailableNFTCollections(
        currentValue: Pair<NFTCollection<NFT.Full>, PaginationRequest>,
        prevValue: Pair<NFTCollection<NFT.Full>?, PaginationRequest?>? = null,
    ): ArrayDeque<NFTsScreenView> {
        val (currentCollection, currentPaginationRequest) = currentValue
        val (prevCollection, _) = prevValue ?: Pair(null, null)

        val currentCollectionViewsList =
            currentCollection.toScreenViewStableList(::onItemClick)

        val prevCollectionsViewsList =
            prevCollection?.toScreenViewStableList(::onItemClick) ?: ArrayDeque()


        if (currentCollection !is NFTCollection.Data) {
            return prevCollectionsViewsList
        } else if (prevCollection !is NFTCollection.Data) {
            return currentCollectionViewsList
        }


        val isCurrentCollectionUserOwned = currentCollection.tokens.firstOrNull()?.isUserOwnedToken == true
        val isPrevCollectionUserOwned = prevCollection.tokens.firstOrNull()?.isUserOwnedToken == true

        return when {
            !isPrevCollectionUserOwned &&
            isCurrentCollectionUserOwned &&
            currentPaginationRequest is PaginationRequest.Prev -> {
                currentCollectionViewsList.apply { addAll(prevCollectionsViewsList) }
            }

            isPrevCollectionUserOwned &&
            !isCurrentCollectionUserOwned &&
            currentPaginationRequest is PaginationRequest.Next -> {
                prevCollectionsViewsList.apply { addAll(currentCollectionViewsList) }
            }

            else -> currentCollectionViewsList
        }
    }

    private fun onCloseClick() = Unit

    private fun onItemClick(token: NFT.Full) = internalNFTRouter.openChooseRecipientScreen(token)

}
package jp.co.soramitsu.nft.impl.presentation.collection

import androidx.compose.runtime.snapshots.SnapshotStateList
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.zipWithPrevious
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult
import jp.co.soramitsu.nft.impl.domain.utils.convertToShareMessage
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.presentation.collection.models.NFTsScreenView
import jp.co.soramitsu.nft.impl.presentation.collection.utils.toScreenViewStableList
import jp.co.soramitsu.nft.navigation.NestedNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
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
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class CollectionNFTsPresenter @Inject constructor(
    private val accountInteractor: AccountInteractor,
    private val chainsRepository: ChainsRepository,
    private val nftInteractor: NFTInteractor,
    private val resourceManager: ResourceManager,
    private val internalNFTRouter: InternalNFTRouter
) {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val screenArgsFlow = internalNFTRouter.createNavGraphRoutesFlow()
        .filterIsInstance<NestedNavGraphRoute.CollectionNFTsScreen>()
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
                }.sample(DEFAULT_SAMPLING_FREQUENCY).filter {
                    isLoadingCompleted.get()
                }.onEach {
                    // Pagination Request Flow can quite many requests in a second, from which we need only one
                    // so we TRY to set isLoadingCompleted, if we don't succeed then
                    // we are already in process of loading
                    isLoadingCompleted.compareAndSet(true, false)
                }.shareIn(this, SharingStarted.Eagerly, 1)

            nftInteractor.collectionNFTsFlow(
                paginationRequestFlow = paginationRequestHelperFlow,
                chainSelectionFlow = screenArgsFlow.distinctUntilChanged().map { it.chainId },
                contractAddressFlow = screenArgsFlow.distinctUntilChanged().map { it.contractAddress },
            ).zipWithPrevious().transform { (prevValue, currentValue) ->
                // each new element indicated that loading has been completed
                isLoadingCompleted.set(true)

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
        currentValue: Pair<NFTCollectionResult, PaginationRequest>,
        prevValue: Pair<NFTCollectionResult?, PaginationRequest?>? = null,
    ): ArrayDeque<NFTsScreenView> {
        val (currentCollection, currentPaginationRequest) = currentValue
        val (prevCollection, _) = prevValue ?: Pair(null, null)

        val currentCollectionViewsList =
            currentCollection.toScreenViewStableList(::onItemClick, ::onActionButtonClick)

        val prevCollectionsViewsList =
            prevCollection?.toScreenViewStableList(::onItemClick, ::onActionButtonClick) ?: ArrayDeque()

        if (currentCollection !is NFTCollectionResult.Data.WithTokens) {
            return prevCollectionsViewsList
        } else if (prevCollection !is NFTCollectionResult.Data.WithTokens) {
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

    private fun onItemClick(token: NFT) = internalNFTRouter.openDetailsNFTScreen(token)

    private fun onActionButtonClick(token: NFT) {
        if (token.isUserOwnedToken) {
            internalNFTRouter.openChooseRecipientScreen(token)
        } else {
            coroutineScope.launch {
                val chain = chainsRepository.getChain(token.chainId)
                val selectedMetaAccount = accountInteractor.selectedMetaAccount()

                val shareMessage = token.convertToShareMessage(
                    resourceManager,
                    null,
                    selectedMetaAccount.address(chain)
                )

                internalNFTRouter.shareText(shareMessage)
            }
        }
    }

    private companion object {
        const val DEFAULT_SAMPLING_FREQUENCY = 1_500L
    }
}
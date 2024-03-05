package jp.co.soramitsu.nft.impl.presentation.collection

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.compose.models.LoadableListPage
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.data.DEFAULT_PAGE_SIZE
import jp.co.soramitsu.nft.impl.domain.utils.convertToShareMessage
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.presentation.CoroutinesStore
import jp.co.soramitsu.nft.impl.presentation.collection.models.NFTsScreenView
import jp.co.soramitsu.nft.impl.presentation.collection.models.ScreenModel
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class CollectionNFTsPresenter @Inject constructor(
    private val accountInteractor: AccountInteractor,
    private val chainsRepository: ChainsRepository,
    private val coroutinesStore: CoroutinesStore,
    private val nftInteractor: NFTInteractor,
    private val resourceManager: ResourceManager,
    private val internalNFTRouter: InternalNFTRouter
) {

    private val screenArgsFlow = internalNFTRouter.createNavGraphRoutesFlow()
        .filterIsInstance<NFTNavGraphRoute.CollectionNFTsScreen>()
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)

    private val mutablePaginationRequestFlow = MutableSharedFlow<PaginationRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val pageScrollingCallback = object : PageScrollingCallback {
        override fun onAllPrevPagesScrolled() {
            mutablePaginationRequestFlow.tryEmit(PaginationRequest.Prev)
        }

        override fun onAllNextPagesScrolled() {
            mutablePaginationRequestFlow.tryEmit(PaginationRequest.Next(100))
        }
    }

    @OptIn(FlowPreview::class)
    fun createCollectionsNFTsFlow(coroutineScope: CoroutineScope): StateFlow<LoadableListPage<NFTsScreenView>> {
        return channelFlow {
            val isLoadingCompleted = AtomicBoolean(true)

            val paginationRequestHelperFlow = mutablePaginationRequestFlow
                .onStart {
                    emit(PaginationRequest.Start(DEFAULT_PAGE_SIZE))
                }.onEach { request ->
                    when (request) {
                        is PaginationRequest.Start -> Unit

                        is PaginationRequest.Prev -> send(ScreenModel.PreviousPageLoading)

                        is PaginationRequest.Next -> send(ScreenModel.NextPageLoading)

                        is PaginationRequest.ProceedFromLastPage -> send(ScreenModel.NextPageLoading)
                    }
                }.debounce(DEFAULT_SAMPLING_FREQUENCY).filter {
                    isLoadingCompleted.get()
                }.onEach {
                    isLoadingCompleted.set(false)
                }.shareIn(this, SharingStarted.Eagerly, 1)

            nftInteractor.tokensFlow(
                paginationRequestFlow = paginationRequestHelperFlow,
                chainSelectionFlow = screenArgsFlow.distinctUntilChanged().map { it.chainId },
                contractAddressFlow = screenArgsFlow.distinctUntilChanged()
                    .map { it.contractAddress },
            ).onEach { collection ->
                // each new element indicated that loading has been completed
                isLoadingCompleted.set(true)

                when (collection) {
                    is NFTCollection.Reloading ->
                        send(ScreenModel.Reloading)

                    is NFTCollection.Loaded.Result ->
                        ScreenModel.ReadyToRender(
                            result = collection,
                            onItemClick = ::onItemClick,
                            onActionButtonClick = ::onActionButtonClick
                        ).also { send(it) }

                    else -> { /* in case of error, or something similar, on this screen do nothing */ }
                }
            }.launchIn(this)
        }.distinctUntilChangedBy { screenModel ->
            screenModel::class.simpleName
        }.flowOn(Dispatchers.Default).stateIn(
            coroutineScope,
            SharingStarted.Lazily,
            ScreenModel.Reloading
        )
    }

    private fun onItemClick(token: NFT) = internalNFTRouter.openDetailsNFTScreen(token)

    private fun onActionButtonClick(token: NFT) {
        if (token.isUserOwnedToken) {
            return internalNFTRouter.openChooseRecipientScreen(token)
        }

        coroutinesStore.uiScope.launch {
            val selectedMetaAccountAddress = accountInteractor.selectedMetaAccount()
                .address(chain = chainsRepository.getChain(token.chainId))

            internalNFTRouter.shareText(
                token.convertToShareMessage(
                    resourceManager,
                    null,
                    selectedMetaAccountAddress
                )
            )
        }
    }

    private companion object {
        const val DEFAULT_SAMPLING_FREQUENCY = 300L
    }
}

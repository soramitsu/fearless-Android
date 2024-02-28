package jp.co.soramitsu.nft.impl.presentation

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.models.LoadableListPage
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.navigation.NavAction
import jp.co.soramitsu.nft.impl.presentation.NFTFlowFragment.Companion.COLLECTION_NAME
import jp.co.soramitsu.nft.impl.presentation.NFTFlowFragment.Companion.CONTRACT_ADDRESS_KEY
import jp.co.soramitsu.nft.impl.presentation.NFTFlowFragment.Companion.SELECTED_CHAIN_ID
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.ChooseNFTRecipientPresenter
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientCallback
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientScreenState
import jp.co.soramitsu.nft.impl.presentation.collection.CollectionNFTsPresenter
import jp.co.soramitsu.nft.impl.presentation.collection.models.NFTsScreenView
import jp.co.soramitsu.nft.impl.presentation.confirmsend.ConfirmNFTSendPresenter
import jp.co.soramitsu.nft.impl.presentation.confirmsend.contract.ConfirmNFTSendCallback
import jp.co.soramitsu.nft.impl.presentation.confirmsend.contract.ConfirmNFTSendScreenState
import jp.co.soramitsu.nft.impl.presentation.details.NftDetailsPresenter
import jp.co.soramitsu.nft.impl.presentation.details.NftDetailsScreenInterface
import jp.co.soramitsu.nft.impl.presentation.details.NftDetailsScreenState
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import jp.co.soramitsu.nft.navigation.NFTRouter
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NFTFlowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    collectionNFTsPresenter: CollectionNFTsPresenter,
    nftDetailsPresenter: NftDetailsPresenter,
    confirmNFTSendPresenter: ConfirmNFTSendPresenter,
    private val coroutinesStore: CoroutinesStore,
    private val chooseNFTRecipientPresenter: ChooseNFTRecipientPresenter,
    private val internalNFTRouter: InternalNFTRouter,
    private val nftRouter: NFTRouter
) : BaseViewModel(),
    NftDetailsScreenInterface by nftDetailsPresenter,
    ChooseNFTRecipientCallback by chooseNFTRecipientPresenter,
    ConfirmNFTSendCallback by confirmNFTSendPresenter {

    val pageScrollingCallback = collectionNFTsPresenter.pageScrollingCallback

    val collectionNFTsScreenState: StateFlow<LoadableListPage<NFTsScreenView>> =
        collectionNFTsPresenter.createCollectionsNFTsFlow(coroutinesStore.uiScope)

    val nftDetailsScreenState: StateFlow<NftDetailsScreenState> =
        nftDetailsPresenter.createScreenStateFlow(coroutinesStore.uiScope)

    val recipientChooserScreenState: StateFlow<ChooseNFTRecipientScreenState> =
        chooseNFTRecipientPresenter.createScreenStateFlow(coroutinesStore.uiScope)

    val confirmSendScreenState: StateFlow<ConfirmNFTSendScreenState> =
        confirmNFTSendPresenter.createScreenStateFlow(coroutinesStore.uiScope)

    val navGraphRoutesFlow: StateFlow<NFTNavGraphRoute> =
        internalNFTRouter.createNavGraphRoutesFlow().stateIn(
            scope = coroutinesStore.uiScope,
            started = SharingStarted.Eagerly,
            initialValue = NFTNavGraphRoute.Loading
        )

    val navGraphActionsFlow: SharedFlow<NavAction> =
        internalNFTRouter.createNavGraphActionsFlow().shareIn(
            scope = coroutinesStore.uiScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    private val selectedChainId = savedStateHandle.get<String>(SELECTED_CHAIN_ID)
        ?: error("Can't find $SELECTED_CHAIN_ID in arguments")

    private val contractAddress = savedStateHandle.get<String>(CONTRACT_ADDRESS_KEY)
        ?: error("Can't find $CONTRACT_ADDRESS_KEY in arguments")

    private val collectionName = savedStateHandle.get<String>(COLLECTION_NAME)
        ?: error("Can't find $COLLECTION_NAME in arguments")

    init {
        internalNFTRouter.openCollectionNFTsScreen(
            selectedChainId = selectedChainId,
            contractAddress = contractAddress
        )
    }

    private val mutableToolbarStateFlow = MutableStateFlow<LoadingState<Pair<TextModel, Int>>>(LoadingState.Loading())
    val toolbarStateFlow: StateFlow<LoadingState<Pair<TextModel, Int>>> = mutableToolbarStateFlow

    fun onDestinationChanged(route: String) {
        val newToolbarState: LoadingState<Pair<TextModel, Int>> = when (route) {
            NFTNavGraphRoute.Loading.routeName ->
                LoadingState.Loading()

            NFTNavGraphRoute.CollectionNFTsScreen.routeName ->
                LoadingState.Loaded(
                    TextModel.SimpleString(collectionName) to R.drawable.ic_cross_24
                )

            NFTNavGraphRoute.DetailsNFTScreen.routeName -> {
                val destinationArgs = internalNFTRouter.destination(NFTNavGraphRoute.DetailsNFTScreen::class.java)
                val title = destinationArgs?.token?.title.orEmpty()

                LoadingState.Loaded(
                    TextModel.SimpleString(title) to R.drawable.ic_arrow_left_24
                )
            }

            NFTNavGraphRoute.ChooseNFTRecipientScreen.routeName ->
                LoadingState.Loaded(
                    TextModel.ResId(R.string.nft_choose_recipient_title) to R.drawable.ic_arrow_left_24
                )

            NFTNavGraphRoute.ConfirmNFTSendScreen.routeName ->
                LoadingState.Loaded(
                    TextModel.ResId(R.string.common_preview) to R.drawable.ic_arrow_left_24
                )

            else -> LoadingState.Loading()
        }

        mutableToolbarStateFlow.value = newToolbarState
    }

    fun onNavigationClick() {
        internalNFTRouter.back()
    }

    fun exitFlow() {
        nftRouter.back()
    }

    fun onQRCodeScannerResult(result: String?) {
        if (result == null) {
            return
        }

        chooseNFTRecipientPresenter.handleQRCodeResult(result)
    }

    override fun onCleared() {
        super.onCleared()
        coroutinesStore.uiScope.cancel()
    }
}

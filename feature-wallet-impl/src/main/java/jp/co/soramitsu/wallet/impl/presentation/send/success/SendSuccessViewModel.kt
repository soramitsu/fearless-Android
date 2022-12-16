package jp.co.soramitsu.wallet.impl.presentation.send.success

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.CustomSnackbarType
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SendSuccessViewModel @Inject constructor(
    private val router: WalletRouter,
    private val chainRegistry: ChainRegistry,
    private val savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager,
    private val externalAccountActions: ExternalAccountActions.Presentation
) : BaseViewModel(),
    Browserable,
    ExternalAccountActions by externalAccountActions,
    SendSuccessScreenInterface {

    val operationHash = savedStateHandle.get<String>(SendSuccessFragment.KEY_OPERATION_HASH)
    val chainId = savedStateHandle.get<ChainId>(SendSuccessFragment.KEY_CHAIN_ID)
    private val customMessage: String? = savedStateHandle[SendSuccessFragment.KEY_CUSTOM_MESSAGE]

    private val _showHashActions = MutableLiveData<Event<Unit>>()
    val showHashActions: LiveData<Event<Unit>> = _showHashActions

    private val _shareUrlEvent = MutableLiveData<Event<String>>()
    val shareUrlEvent = _shareUrlEvent

    private val chainExplorers = flow {
        chainId?.let {
            emit(chainRegistry.getChain(it).explorers)
        }
    }.share()

    private val subscanUrlFlow = chainExplorers.map {
        operationHash ?: return@map null
        it.firstOrNull { it.type == Chain.Explorer.Type.SUBSCAN }?.let {
            BlockExplorerUrlBuilder(it.url, it.types).build(BlockExplorerUrlBuilder.Type.EXTRINSIC, operationHash)
        }
    }

    val state: StateFlow<SendSuccessViewState> = subscanUrlFlow.map { url ->
        SendSuccessViewState(
            message = customMessage ?: resourceManager.getString(R.string.send_success_message),
            tableItems = getInfoTableItems(),
            isShowSubscanButtons = url.isNullOrEmpty().not()
        )
    }.stateIn(this, SharingStarted.Eagerly, SendSuccessViewState.default)

    private fun getInfoTableItems() = listOf(
        TitleValueViewState(
            title = resourceManager.getString(R.string.hash),
            value = operationHash?.shorten(),
            clickState = TitleValueViewState.ClickState(R.drawable.ic_copy_filled_24, SendSuccessViewState.CODE_HASH_CLICK)
        )
    )

    override fun onClose() {
        router.back()
    }

    override fun onItemClick(code: Int) {
        when (code) {
            SendSuccessViewState.CODE_HASH_CLICK -> operationHash?.let { copyString(it) }
        }
    }

    override fun onSubscanClick() {
        launch {
            subscanUrlFlow.first()?.let { url ->
                openUrl(url)
            }
        }
    }

    override fun onShareClick() {
        launch {
            subscanUrlFlow.first()?.let { url ->
                _shareUrlEvent.value = Event(url)
            }
        }
    }

    fun getSupportedExplorers(type: BlockExplorerUrlBuilder.Type, value: String) =
        chainExplorers.replayCache.firstOrNull()?.getSupportedExplorers(type, value).orEmpty()

    fun copyString(value: String) {
        clipboardManager.addToClipboard(value)

        showSnackbar(CustomSnackbarType.COMMON_COPIED)
    }

    override val openBrowserEvent: MutableLiveData<Event<String>> = MutableLiveData()

    fun openUrl(url: String) {
        openBrowserEvent.value = Event(url)
    }
}

private fun String.shorten() = when {
    length < 20 -> this
    else -> "${take(5)}...${takeLast(5)}"
}

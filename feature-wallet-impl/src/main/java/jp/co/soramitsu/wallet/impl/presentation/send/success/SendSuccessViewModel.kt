package jp.co.soramitsu.wallet.impl.presentation.send.success

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

@HiltViewModel
class SendSuccessViewModel @Inject constructor(
    private val router: WalletRouter,
    private val chainRegistry: ChainRegistry,
    private val savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager
) : BaseViewModel(),
    Browserable,
    SendSuccessScreenInterface {

    val operationHash = savedStateHandle.get<String>(SendSuccessFragment.KEY_OPERATION_HASH)
    val chainId = savedStateHandle.get<ChainId>(SendSuccessFragment.KEY_CHAIN_ID)

    private val _showHashActions = MutableLiveData<Event<Unit>>()
    val showHashActions: LiveData<Event<Unit>> = _showHashActions

    val state: StateFlow<SendSuccessViewState> = MutableStateFlow(value = getSendSuccessViewState())

    private fun getSendSuccessViewState(): SendSuccessViewState {
        val hashInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.hash),
            value = operationHash?.shorten(),
            clickState = TitleValueViewState.ClickState(R.drawable.ic_arrow_top_right_white_16, SendSuccessViewState.CODE_HASH_CLICK)
        )

        val resultInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.result),
            value = "Success"
        )

        val infoTableItems = listOf(hashInfoItem, resultInfoItem)

        return SendSuccessViewState(tableItems = infoTableItems)
    }

    override fun onClose() {
        router.back()
    }

    override fun onHashClick() {
        _showHashActions.value = Event(Unit)
    }

    private val chainExplorers = flow {
        chainId?.let {
            emit(chainRegistry.getChain(it).explorers)
        }
    }.share()

    fun getSupportedExplorers(type: BlockExplorerUrlBuilder.Type, value: String) =
        chainExplorers.replayCache.firstOrNull()?.getSupportedExplorers(type, value).orEmpty()

    fun copyStringClicked(address: String) {
        clipboardManager.addToClipboard(address)

        showMessage(resourceManager.getString(R.string.common_copied))
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

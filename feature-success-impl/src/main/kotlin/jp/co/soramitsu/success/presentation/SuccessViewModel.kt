package jp.co.soramitsu.success.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatting.shortenHash
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SuccessViewModel @Inject constructor(
    private val router: SuccessRouter,
    private val chainRegistry: ChainRegistry,
    savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager,
    private val externalAccountActions: ExternalAccountActions.Presentation
) : BaseViewModel(),
    Browserable,
    ExternalAccountActions by externalAccountActions,
    SuccessScreenInterface {

    val operationHash = savedStateHandle.get<String>(SuccessFragment.KEY_OPERATION_HASH)
    val chainId = savedStateHandle.get<ChainId>(SuccessFragment.KEY_CHAIN_ID)
    private val customMessage: String? = savedStateHandle[SuccessFragment.KEY_CUSTOM_MESSAGE]
    private val hasSuccessResult: Boolean = savedStateHandle[SuccessFragment.KEY_HAS_SUCCESS_RESULT] ?: true

    private val _showHashActions = MutableLiveData<Event<Unit>>()
    val showHashActions: LiveData<Event<Unit>> = _showHashActions

    private val _shareUrlEvent = MutableLiveData<Event<String>>()
    val shareUrlEvent = _shareUrlEvent

    private val chainExplorers = flow {
        chainId?.let {
            emit(chainRegistry.getChain(it).explorers)
        }
    }.share()

    private val explorerPairFlow = chainExplorers.map {
        operationHash ?: return@map null
        it.firstNotNullOfOrNull { explorerItem ->
            when (explorerItem.type) {
                Chain.Explorer.Type.POLKASCAN,
                Chain.Explorer.Type.SUBSCAN,
                Chain.Explorer.Type.REEF -> {
                    BlockExplorerUrlBuilder(explorerItem.url, explorerItem.types).build(BlockExplorerUrlBuilder.Type.EXTRINSIC, operationHash)
                }
                Chain.Explorer.Type.OKLINK,
                Chain.Explorer.Type.ETHERSCAN,
                Chain.Explorer.Type.ZETA -> {
                    BlockExplorerUrlBuilder(explorerItem.url, explorerItem.types).build(BlockExplorerUrlBuilder.Type.TX, operationHash)
                }

                Chain.Explorer.Type.UNKNOWN -> null
            }?.let { url ->
                explorerItem.type to url
            }
        }
    }.stateIn(this, SharingStarted.Eagerly, Pair(Chain.Explorer.Type.UNKNOWN, ""))

    val state: StateFlow<SuccessViewState> = explorerPairFlow.map { explorer ->
        SuccessViewState(
            message = customMessage ?: resourceManager.getString(R.string.return_to_app_message),
            tableItems = getInfoTableItems(),
            explorer = explorer
        )
    }.stateIn(this, SharingStarted.Eagerly, SuccessViewState.default)

    private fun getInfoTableItems() = operationHash?.let {
        listOf(
            TitleValueViewState(
                title = resourceManager.getString(R.string.hash),
                value = operationHash.shortenHash(),
                clickState = TitleValueViewState.ClickState.Value(R.drawable.ic_copy_filled_24, SuccessViewState.CODE_HASH_CLICK)
            ),
            TitleValueViewState(
                title = resourceManager.getString(R.string.all_done_alert_result_stub),
                value = resourceManager.getString(R.string.all_done_alert_success_stub),
                valueColor = greenText
            )
        )
    }.orEmpty()

    override fun onClose() {
        launch {
            chainId?.let {
                if (chainRegistry.getChain(chainId).isEthereumChain) {
                    BalanceUpdateTrigger.invoke(chainId, true)
                }
            }
            router.back()
        }
    }

    override fun onItemClick(code: Int) {
        when (code) {
            SuccessViewState.CODE_HASH_CLICK -> operationHash?.let { copyString(it) }
        }
    }

    override fun onExplorerClick() {
        launch {
            explorerPairFlow.value?.let { (_, url) ->
                openUrl(url)
            }
        }
    }

    override fun onShareClick() {
        launch {
            explorerPairFlow.value?.let { (_, url) ->
                _shareUrlEvent.value = Event(url)
            }
        }
    }

    fun getSupportedExplorers(type: BlockExplorerUrlBuilder.Type, value: String) =
        chainExplorers.replayCache.firstOrNull()?.getSupportedExplorers(type, value).orEmpty()

    fun copyString(value: String) {
        clipboardManager.addToClipboard(value)

        showMessage(resourceManager.getString(R.string.common_copied))
    }

    override val openBrowserEvent: MutableLiveData<Event<String>> = MutableLiveData()

    fun openUrl(url: String) {
        openBrowserEvent.value = Event(url)
    }
}

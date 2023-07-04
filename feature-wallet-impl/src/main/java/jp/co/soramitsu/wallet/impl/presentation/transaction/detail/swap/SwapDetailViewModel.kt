package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.toMarkets
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoDetailFromPlanks
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class SwapDetailViewModel @Inject constructor(
    private val router: WalletRouter,
    private val chainRegistry: ChainRegistry,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(), SwapDetailCallbacks, Browserable {

    companion object {
        const val CODE_HASH_CLICK = 1
    }

    override val openBrowserEvent: MutableLiveData<Event<String>> = MutableLiveData()
    private val _shareUrlEvent = MutableLiveData<Event<String>>()
    val shareUrlEvent = _shareUrlEvent

    private val swap = savedStateHandle.get<OperationParcelizeModel.Swap>(SwapDetailFragment.KEY_SWAP) ?: error("Swap detail not specified")

    private val swapRate = kotlin.runCatching {
        swap.targetAsset?.amountFromPlanks(swap.targetAssetAmount.orZero()).orZero().divide(swap.chainAsset.amountFromPlanks(swap.baseAssetAmount))
    }.getOrNull() ?: BigDecimal.ZERO

    private val chainExplorers = flow {
        emit(chainRegistry.getChain(swap.chainAsset.chainId).explorers)
    }.share()

    private val subscanUrlFlow = chainExplorers.map {
        it.firstOrNull { it.type == Chain.Explorer.Type.SUBSCAN }?.let {
            BlockExplorerUrlBuilder(it.url, it.types).build(BlockExplorerUrlBuilder.Type.EXTRINSIC, swap.hash)
        }
    }

    private val initialState = SwapDetailState(
        fromTokenImage = GradientIconState.Remote(swap.chainAsset.iconUrl, swap.chainAsset.color),
        toTokenImage = GradientIconState.Remote(swap.targetAsset?.iconUrl.orEmpty(), swap.targetAsset?.color.orEmpty()),
        fromTokenAmount = swap.baseAssetAmount.formatCryptoFromPlanks(swap.chainAsset),
        toTokenAmount = swap.targetAsset?.let { swap.targetAssetAmount?.formatCryptoFromPlanks(it) } ?: "???",
        fromTokenName = swap.chainAsset.symbol.uppercase(),
        toTokenName = swap.targetAsset?.symbol?.uppercase() ?: "???",
        statusAppearance = swap.status.mapToStatusAppearance(),
        address = swap.address,
        hash = swap.hash,
        fromTokenOnToToken = swapRate.formatCryptoDetail(),
        liquidityProviderFee = swap.liquidityProviderFee.formatCryptoDetailFromPlanks(swap.chainAsset),
        networkFee = swap.networkFee.formatCryptoDetailFromPlanks(swap.chainAsset),
        time = swap.time,
        market = swap.selectedMarket?.let { listOf(it).toMarkets().firstOrNull() } ?: Market.SMART,
        isShowSubscanButtons = false
    )

    val state = subscanUrlFlow.map { url ->
        initialState.copy(isShowSubscanButtons = url.isNullOrEmpty().not())
    }.stateIn(this, SharingStarted.Eagerly, initialState)

    override fun onBackClick() {
        router.back()
    }

    override fun onCloseClick() {
        onBackClick()
    }

    override fun onItemClick(code: Int) {
        when (code) {
            CODE_HASH_CLICK -> {
                copyString(swap.hash)
            }
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

    private fun openUrl(url: String) {
        openBrowserEvent.value = Event(url)
    }

    private fun copyString(value: String) {
        clipboardManager.addToClipboard(value)

        showMessage(resourceManager.getString(jp.co.soramitsu.common.R.string.common_copied))
    }
}

enum class SwapStatusAppearance(
    val color: Color,
    @StringRes val labelRes: Int
) {
    COMPLETED(greenText, R.string.all_done_alert_success_stub),
    PENDING(white, R.string.transaction_status_pending),
    FAILED(white, R.string.transaction_status_failed)
}

private fun Operation.Status.mapToStatusAppearance() = when (this) {
    Operation.Status.COMPLETED -> SwapStatusAppearance.COMPLETED
    Operation.Status.PENDING -> SwapStatusAppearance.PENDING
    Operation.Status.FAILED -> SwapStatusAppearance.FAILED
}

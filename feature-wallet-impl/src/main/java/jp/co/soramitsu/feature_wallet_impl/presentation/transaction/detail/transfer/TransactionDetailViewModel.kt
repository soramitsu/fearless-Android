package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.transfer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.liveData
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

private const val ICON_SIZE_DP = 32

enum class ExternalActionsSource {
    TRANSACTION_HASH, FROM_ADDRESS, TO_ADDRESS
}

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val clipboardManager: ClipboardManager,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val chainRegistry: ChainRegistry,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), Browserable {

    val operation = savedStateHandle.getLiveData<OperationParcelizeModel.Transfer>(KEY_TRANSACTION)
    val assetPayload = savedStateHandle.getLiveData<AssetPayload>(KEY_ASSET_PAYLOAD)

    private val _showExternalViewEvent = MutableLiveData<Event<ExternalActionsSource>>()
    val showExternalTransactionActionsEvent: LiveData<Event<ExternalActionsSource>> = _showExternalViewEvent

    override val openBrowserEvent: MutableLiveData<Event<String>> = MutableLiveData()

    val recipientAddressModelLiveData = liveData {
        emit(getIcon(operation.value!!.receiver))
    }

    val senderAddressModelLiveData = liveData {
        emit(getIcon(operation.value!!.sender))
    }

    private val chainExplorers = flow { emit(chainRegistry.getChain(assetPayload.value!!.chainId).explorers) }.share()

    fun getSupportedExplorers(type: BlockExplorerUrlBuilder.Type, value: String) =
        chainExplorers.replayCache.firstOrNull()?.getSupportedExplorers(type, value).orEmpty()

    val retryAddressModelLiveData = if (operation.value!!.isIncome) senderAddressModelLiveData else recipientAddressModelLiveData

    fun copyStringClicked(address: String) {
        clipboardManager.addToClipboard(address)

        showMessage(resourceManager.getString(R.string.common_copied))
    }

    fun backClicked() {
        router.back()
    }

    fun repeatTransaction() {
        val retryAddress = retryAddressModelLiveData.value?.address ?: return

        router.openRepeatTransaction(retryAddress, assetPayload.value!!)
    }

    private suspend fun getIcon(address: String) = addressIconGenerator.createAddressModel(address, ICON_SIZE_DP, addressDisplayUseCase(address))

    fun showExternalActionsClicked(externalActionsSource: ExternalActionsSource) {
        _showExternalViewEvent.value = Event(externalActionsSource)
    }

    fun openUrl(url: String) {
        openBrowserEvent.value = Event(url)
    }
}

package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import kotlinx.coroutines.flow.flow

private const val ICON_SIZE_DP = 32

enum class ExternalActionsSource {
    TRANSACTION_HASH, FROM_ADDRESS
}

class ExtrinsicDetailViewModel @AssistedInject constructor(
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    val chainRegistry: ChainRegistry,
    @Assisted val payload: ExtrinsicDetailsPayload
) : BaseViewModel(), Browserable {
    private val _showExternalViewEvent = MutableLiveData<Event<ExternalActionsSource>>()
    val showExternalExtrinsicActionsEvent: LiveData<Event<ExternalActionsSource>> = _showExternalViewEvent

    override val openBrowserEvent: MutableLiveData<Event<String>> = MutableLiveData()

    val fromAddressModelLiveData = liveData {
        emit(getIcon(payload.operation.originAddress))
    }

    private val chainExplorers = flow { emit(chainRegistry.getChain(payload.chainId).explorers) }.share()

    fun getSupportedExplorers(type: BlockExplorerUrlBuilder.Type, value: String) =
        chainExplorers.replayCache.firstOrNull()?.getSupportedExplorers(type, value).orEmpty()

    private suspend fun getIcon(address: String) = addressIconGenerator.createAddressModel(
        address,
        ICON_SIZE_DP, addressDisplayUseCase(address)
    )

    fun openUrl(url: String) {
        openBrowserEvent.value = Event(url)
    }

    fun copyStringClicked(address: String) {
        clipboardManager.addToClipboard(address)

        showMessage(resourceManager.getString(R.string.common_copied))
    }

    fun showExternalActionsClicked(externalActionsSource: ExternalActionsSource) {
        _showExternalViewEvent.value = Event(externalActionsSource)
    }

    fun backClicked() {
        router.back()
    }

    @AssistedFactory
    interface ExtrinsicDetailViewModelFactory {
        fun create(payload: ExtrinsicDetailsPayload): ExtrinsicDetailViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            factory: ExtrinsicDetailViewModelFactory,
            payload: ExtrinsicDetailsPayload
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return factory.create(payload) as T
            }
        }
    }
}

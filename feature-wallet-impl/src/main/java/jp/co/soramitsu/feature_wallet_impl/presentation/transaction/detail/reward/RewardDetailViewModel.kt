package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.reward

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
    TRANSACTION_HASH, VALIDATOR_ADDRESS
}

class RewardDetailViewModel @AssistedInject constructor(
    @Assisted val payload: RewardDetailsPayload,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val router: WalletRouter,
    private val chainRegistry: ChainRegistry,
) : BaseViewModel(), Browserable {

    private val _showExternalViewEvent = MutableLiveData<Event<ExternalActionsSource>>()
    val showExternalRewardActionsEvent: LiveData<Event<ExternalActionsSource>> = _showExternalViewEvent

    override val openBrowserEvent: MutableLiveData<Event<String>> = MutableLiveData()

    val validatorAddressModelLiveData = liveData {
        val icon = payload.operation.validator?.let { getIcon(it) }

        emit(icon)
    }

    val eraLiveData = liveData {
        emit(resourceManager.getString(R.string.staking_era_index_no_prefix, payload.operation.era))
    }

    private val chainExplorers = flow { emit(chainRegistry.getChain(payload.chainId).explorers) }.share()

    fun getSupportedExplorers(type: BlockExplorerUrlBuilder.Type, value: String) =
        chainExplorers.replayCache.firstOrNull()?.getSupportedExplorers(type, value).orEmpty()

    private suspend fun getIcon(address: String) = addressIconGenerator.createAddressModel(address, ICON_SIZE_DP, addressDisplayUseCase(address))

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
    interface RewardDetailViewModelFactory {
        fun create(payload: RewardDetailsPayload): RewardDetailViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            factory: RewardDetailViewModelFactory,
            payload: RewardDetailsPayload
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return factory.create(payload) as T
            }
        }
    }
}

package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.reward

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_account_api.presenatation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel

private const val ICON_SIZE_DP = 32

enum class ExternalActionsSource {
    TRANSACTION_HASH, VALIDATOR_ADDRESS
}

class RewardDetailViewModel(
    val operation: OperationParcelizeModel.Reward,
    private val appLinksProvider: AppLinksProvider,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val router: WalletRouter,
) : BaseViewModel(), Browserable {

    private val _showExternalViewEvent = MutableLiveData<Event<ExternalActionsSource>>()
    val showExternalRewardActionsEvent: LiveData<Event<ExternalActionsSource>> = _showExternalViewEvent

    override val openBrowserEvent: MutableLiveData<Event<String>> = MutableLiveData()

    val validatorAddressModelLiveData = liveData {
        val icon = operation.validator?.let { getIcon(it) }

        emit(icon)
    }

    val eraLiveData = liveData {
        emit(resourceManager.getString(R.string.staking_era_index_no_prefix, operation.era))
    }

    private suspend fun getIcon(address: String) = addressIconGenerator.createAddressModel(address, ICON_SIZE_DP, addressDisplayUseCase(address))

    fun viewEventExternalClicked(analyzer: ExternalAnalyzer, hash: String, networkType: Node.NetworkType) {
        val url = appLinksProvider.getExternalEventUrl(analyzer, hash, networkType)

        url?.let {
            openBrowserEvent.value = Event(it)
        }
    }

    fun viewAccountExternalClicked(analyzer: ExternalAnalyzer, address: String, networkType: Node.NetworkType) {
        val url = appLinksProvider.getExternalAddressUrl(analyzer, address, networkType)

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
}

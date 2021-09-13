package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic

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
    TRANSACTION_HASH, FROM_ADDRESS
}

class ExtrinsicDetailViewModel(
    private val appLinksProvider: AppLinksProvider,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val addressIconGenerator: AddressIconGenerator,
    private val router: WalletRouter,
    val operation: OperationParcelizeModel.Extrinsic,
) : BaseViewModel(), Browserable {
    private val _showExternalViewEvent = MutableLiveData<Event<ExternalActionsSource>>()
    val showExternalExtrinsicActionsEvent: LiveData<Event<ExternalActionsSource>> = _showExternalViewEvent

    override val openBrowserEvent: MutableLiveData<Event<String>> = MutableLiveData()

    val fromAddressModelLiveData = liveData {
        emit(getIcon(operation.originAddress))
    }

    private suspend fun getIcon(address: String) = addressIconGenerator.createAddressModel(
        address,
        ICON_SIZE_DP, addressDisplayUseCase(address)
    )

    fun viewTransactionExternalClicked(analyzer: ExternalAnalyzer, hash: String, networkType: Node.NetworkType) {
        val url = appLinksProvider.getExternalTransactionUrl(analyzer, hash, networkType)

        openBrowserEvent.value = Event(url)
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

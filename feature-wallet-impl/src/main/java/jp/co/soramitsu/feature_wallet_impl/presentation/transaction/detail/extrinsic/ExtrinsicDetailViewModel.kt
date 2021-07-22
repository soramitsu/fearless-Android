package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationModel

enum class ExternalActionsSource {
    TRANSACTION_HASH, FROM_ADDRESS
}

class ExtrinsicDetailViewModel(
    val operation: OperationModel,
    private val appLinksProvider: AppLinksProvider,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    private val router: WalletRouter,

    ) : BaseViewModel(), Browserable {
    private val _showExternalViewEvent = MutableLiveData<Event<ExternalActionsSource>>()
    val showExternalExtrinsicActionsEvent: LiveData<Event<ExternalActionsSource>> = _showExternalViewEvent

    override val openBrowserEvent: MutableLiveData<Event<String>> = MutableLiveData()

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

    fun showExternalActionsClicked(externalActionsSource: ExternalActionsSource){
        _showExternalViewEvent.value = Event(externalActionsSource)
    }

    fun backClicked() {
        router.back()
    }

}

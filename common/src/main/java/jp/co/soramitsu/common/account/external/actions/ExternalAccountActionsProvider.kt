package jp.co.soramitsu.common.account.external.actions

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.model.Node

class ExternalAccountActionsProvider(
    val clipboardManager: ClipboardManager,
    val appLinksProvider: AppLinksProvider,
    val resourceManager: ResourceManager
) : ExternalAccountActions.Presentation {
    override val openBrowserEvent = MutableLiveData<Event<String>>()

    override val showExternalActionsEvent = MutableLiveData<Event<ExternalAccountActions.Payload>>()

    override fun showBrowser(url: String) {
        openBrowserEvent.value = Event(url)
    }

    override fun showExternalActions(payload: ExternalAccountActions.Payload) {
        showExternalActionsEvent.value = Event(payload)
    }

    override fun copyAddress(address: String, messageShower: (message: String) -> Unit) {
        clipboardManager.addToClipboard(address)

        val message = resourceManager.getString(R.string.common_copied)

        messageShower.invoke(message)
    }

    override fun viewExternalClicked(analyzer: ExternalAnalyzer, address: String, networkType: Node.NetworkType) {
        val url = appLinksProvider.getExternalAddressUrl(analyzer, address, networkType)

        openBrowserEvent.value = Event(url)
    }
}
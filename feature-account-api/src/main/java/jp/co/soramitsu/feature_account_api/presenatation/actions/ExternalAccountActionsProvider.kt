package jp.co.soramitsu.feature_account_api.presenatation.actions

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

class ExternalAccountActionsProvider(
    val clipboardManager: ClipboardManager,
    val appLinksProvider: AppLinksProvider,
    val resourceManager: ResourceManager,
    val chainRegistry: ChainRegistry
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

    override fun viewExternalClicked(url: String) {
        openBrowserEvent.value = Event(url)
    }
}

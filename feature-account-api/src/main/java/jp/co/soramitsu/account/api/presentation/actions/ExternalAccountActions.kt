package jp.co.soramitsu.account.api.presentation.actions

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface ExternalAccountActions : Browserable {

    open class Payload(
        val value: String,
        val explorers: Map<Chain.Explorer.Type, String>
    )

    val showExternalActionsEvent: LiveData<Event<Payload>>

    fun viewExternalClicked(url: String)

    fun copyAddress(address: String, messageShower: (message: String) -> Unit)

    interface Presentation : ExternalAccountActions, Browserable.Presentation {

        fun showExternalActions(payload: Payload)
    }
}

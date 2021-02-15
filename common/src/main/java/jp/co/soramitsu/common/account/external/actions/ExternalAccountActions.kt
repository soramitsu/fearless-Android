package jp.co.soramitsu.common.account.external.actions

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event

interface ExternalAccountActions : Browserable {

    class Payload(
        val value: String,
        val networkType: jp.co.soramitsu.domain.model.Node.NetworkType
    )

    val showExternalActionsEvent: LiveData<Event<Payload>>

    fun viewExternalClicked(analyzer: ExternalAnalyzer, address: String, networkType: jp.co.soramitsu.domain.model.Node.NetworkType)

    fun copyAddress(address: String, messageShower: (message: String) -> Unit)

    interface Presentation : ExternalAccountActions, Browserable.Presentation {
        fun showExternalActions(payload: Payload)
    }
}
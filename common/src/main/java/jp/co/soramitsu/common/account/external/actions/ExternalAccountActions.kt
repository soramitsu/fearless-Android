package jp.co.soramitsu.common.account.external.actions

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.model.Node

interface ExternalAccountActions : Browserable {

    class Payload(
        val value: String,
        val networkType: Node.NetworkType
    )

    val showExternalActionsEvent: LiveData<Event<Payload>>

    fun viewExternalClicked(analyzer: ExternalAnalyzer, address: String, networkType: Node.NetworkType)

    fun copyAddress(address: String, messageShower: (message: String) -> Unit)

    interface Presentation : ExternalAccountActions, Browserable.Presentation {
        fun showExternalActions(payload: Payload)
    }
}
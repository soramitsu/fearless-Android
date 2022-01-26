package jp.co.soramitsu.feature_account_api.presenatation.actions

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface ExternalAccountActions : Browserable {

    class Payload(
        val value: String,
        @Deprecated("Legacy", ReplaceWith("Chain from Json"))
        val networkType: Node.NetworkType?,
        val chainId: ChainId? = null,
        val chainName: String? = null,
    ) {
        companion object {

            fun fromAddress(address: String) = Payload(address, address.networkType())
        }
    }

    val showExternalActionsEvent: LiveData<Event<Payload>>

    fun viewExternalClicked(analyzer: ExternalAnalyzer, address: String, networkType: Node.NetworkType)

    fun copyAddress(address: String, messageShower: (message: String) -> Unit)

    interface Presentation : ExternalAccountActions, Browserable.Presentation {

        fun showExternalActions(payload: Payload)
    }
}

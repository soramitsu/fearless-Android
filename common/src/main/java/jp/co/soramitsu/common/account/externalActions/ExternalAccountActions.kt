package jp.co.soramitsu.common.account.externalActions

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node

interface ExternalAccountActions : Browserable {

    val showExternalActionsEvent: LiveData<Event<Account>>

    fun viewExternalClicked(analyzer: ExternalAnalyzer, address: String, networkType: Node.NetworkType)

    fun copyAddress(address: String, messageShower: (message: String) -> Unit)

    interface Presentation : ExternalAccountActions, Browserable.Presentation {
        fun showExternalActions(account: Account)
    }
}
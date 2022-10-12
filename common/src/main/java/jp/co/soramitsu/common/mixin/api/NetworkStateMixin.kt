package jp.co.soramitsu.common.mixin.api

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.compose.component.NetworkIssueItemState

interface NetworkStateMixin : NetworkStateUi

interface NetworkStateUi {
    val showConnectingBarLiveData: LiveData<Boolean>
    val networkIssuesLiveData: LiveData<List<NetworkIssueItemState>>

    fun updateShowConnecting(isShow: Boolean)

    fun updateNetworkIssues(list: List<NetworkIssueItemState>)
}

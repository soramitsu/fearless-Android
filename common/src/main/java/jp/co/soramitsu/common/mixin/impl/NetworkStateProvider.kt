package jp.co.soramitsu.common.mixin.impl

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.compose.component.NetworkIssueItemState
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin

class NetworkStateProvider : NetworkStateMixin {
    override val showConnectingBarLiveData = MutableLiveData(false)

    override val networkIssuesLiveData = MutableLiveData<List<NetworkIssueItemState>>()

    override val chainConnectionsLiveData = MutableLiveData<Map<String, Boolean>>()

    override fun updateShowConnecting(isShow: Boolean) {
        showConnectingBarLiveData.postValue(isShow)
    }

    override fun updateNetworkIssues(list: List<NetworkIssueItemState>) {
        networkIssuesLiveData.postValue(list)
    }

    override fun updateChainConnection(map: Map<String, Boolean>) {
        chainConnectionsLiveData.postValue(map)
    }
}

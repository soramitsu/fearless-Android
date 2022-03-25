package jp.co.soramitsu.common.mixin.impl

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin

class NetworkStateProvider : NetworkStateMixin {
    override val showConnectingBarLiveData = MutableLiveData(false)

    override fun updateShowConnecting(isShow: Boolean) {
        showConnectingBarLiveData.postValue(isShow)
    }
}

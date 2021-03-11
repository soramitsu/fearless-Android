package jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.coroutines.CoroutineScope

interface NetworkChooserMixin {

    val selectedNetworkLiveData: MutableLiveData<NetworkModel>

    val networkChooserEvent: LiveData<Event<Payload<NetworkModel>>>

    val isNetworkTypeChangeAvailable: Boolean

    fun chooseNetworkClicked(scope: CoroutineScope)
}

fun <T> T.chooseNetworkClicked() where T : CoroutineScope, T : NetworkChooserMixin {
    chooseNetworkClicked(this)
}

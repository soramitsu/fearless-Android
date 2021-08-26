package jp.co.soramitsu.feature_account_impl.presentation.common.mixin.impl

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.mediatorLiveData
import jp.co.soramitsu.common.utils.updateFrom
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.data.mappers.mapNetworkTypeToNetworkModel
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NetworkChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkChooser(
    private val interactor: AccountInteractor,
    private val forcedNetworkType: Node.NetworkType?
) : NetworkChooserMixin {

    override val isNetworkTypeChangeAvailable = forcedNetworkType == null

    override val selectedNetworkLiveData = mediatorLiveData<NetworkModel> {
        updateFrom(initialNetworkTypeLiveData())
    }

    private val _networkChooserEvent = MutableLiveData<Event<Payload<NetworkModel>>>()
    override val networkChooserEvent: LiveData<Event<Payload<NetworkModel>>> = _networkChooserEvent

    override fun chooseNetworkClicked(scope: CoroutineScope) {
        val selectedNode = selectedNetworkLiveData.value!!

        scope.launch {
            val networkModels = getNetworkModels()

            _networkChooserEvent.value = Event(Payload(networkModels, selectedNode))
        }
    }

    private fun initialNetworkTypeLiveData() = liveData {
        val selectedType = getNetworkType()
        val mapped = mapNetworkTypeToNetworkModel(selectedType)

        emit(mapped)
    }

    private suspend fun getNetworkModels() = withContext(Dispatchers.Default) {
        interactor.getNetworks().map { mapNetworkTypeToNetworkModel(it.type) }
    }

    private suspend fun getNetworkType() = forcedNetworkType ?: interactor.selectedNetworkType()
}

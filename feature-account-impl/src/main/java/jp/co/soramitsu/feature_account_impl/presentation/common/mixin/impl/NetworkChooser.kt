package jp.co.soramitsu.feature_account_impl.presentation.common.mixin.impl

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.common.utils.asMutableLiveData
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.data.mappers.mapNetworkTypeToNetworkModel
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NetworkChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel

class NetworkChooser(
    private val interactor: AccountInteractor,
    private val selectedNetworkType: Node.NetworkType?
) : NetworkChooserMixin {

    override val networkDisposable = CompositeDisposable()

    private val networkModelsLiveData = getNetworkModels().asLiveData(networkDisposable)

    override val isNetworkTypeChangeAvailable = selectedNetworkType == null

    override val selectedNetworkLiveData = getNetworkType()
        .subscribeOn(Schedulers.io())
        .map(::mapNetworkTypeToNetworkModel)
        .observeOn(AndroidSchedulers.mainThread())
        .asMutableLiveData(networkDisposable)

    private val _networkChooserEvent = MutableLiveData<Event<Payload<NetworkModel>>>()
    override val networkChooserEvent: LiveData<Event<Payload<NetworkModel>>> = _networkChooserEvent

    override fun chooseNetworkClicked() {
        val selectedNode = selectedNetworkLiveData.value
        val networkModels = networkModelsLiveData.value

        if (selectedNode != null && networkModels != null) {
            _networkChooserEvent.value = Event(Payload(networkModels, selectedNode))
        }
    }

    private fun getNetworkModels(): Single<List<NetworkModel>> {
        return interactor.getNetworks()
            .subscribeOn(Schedulers.io())
            .mapList { mapNetworkTypeToNetworkModel(it.type) }
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun getNetworkType() = if (selectedNetworkType == null) {
        interactor.getSelectedNetworkType()
    } else {
        Single.just(selectedNetworkType)
    }
}
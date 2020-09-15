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
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_impl.presentation.common.mapNetworkToNetworkModel
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.NetworkChooserMixin
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.NetworkChooserPayload
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel

class NetworkChooser(
    private val interactor: AccountInteractor
) : NetworkChooserMixin {
    override val networkDisposable = CompositeDisposable()

    private val networkModelsLiveData = getNetworkModels().asLiveData(networkDisposable)

    override val selectedNetworkLiveData = interactor.getSelectedNetwork()
        .map(::mapNetworkToNetworkModel)
        .asMutableLiveData(networkDisposable)

    private val _networkChooserEvent = MutableLiveData<Event<NetworkChooserPayload>>()
    override val networkChooserEvent: LiveData<Event<NetworkChooserPayload>> = _networkChooserEvent

    override fun chooseNetworkClicked() {
        val selectedNode = selectedNetworkLiveData.value
        val networkModels = networkModelsLiveData.value

        if (selectedNode != null && networkModels != null) {
            _networkChooserEvent.value = Event(NetworkChooserPayload(networkModels, selectedNode))
        }
    }

    private fun getNetworkModels(): Single<List<NetworkModel>> {
        return interactor.getNetworks()
            .subscribeOn(Schedulers.io())
            .map { it.map(::mapNetworkToNetworkModel) }
            .observeOn(AndroidSchedulers.mainThread())
    }
}
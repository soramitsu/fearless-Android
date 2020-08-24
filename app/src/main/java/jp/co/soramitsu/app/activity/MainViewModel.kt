package jp.co.soramitsu.app.activity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.app.activity.domain.MainInteractor
import jp.co.soramitsu.app.navigation.Destination
import jp.co.soramitsu.common.base.BaseViewModel

class MainViewModel(
    private val interactor: MainInteractor
) : BaseViewModel() {

    private val _navigationDestinationLiveData = MutableLiveData<Destination>()
    val navigationDestinationLiveData: LiveData<Destination> = _navigationDestinationLiveData

    init {
        disposables.add(
            interactor.getNavigationDestination()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _navigationDestinationLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun jsonFileOpened(content: String?) {}
}
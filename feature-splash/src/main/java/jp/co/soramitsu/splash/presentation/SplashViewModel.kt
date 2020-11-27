package jp.co.soramitsu.splash.presentation

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.splash.SplashRouter

class SplashViewModel(
    private val router: SplashRouter,
    private val repository: AccountRepository
) : BaseViewModel() {

    private val _removeSplashBackgroundLiveData = MutableLiveData<Event<Unit>>()
    val removeSplashBackgroundLiveData = _removeSplashBackgroundLiveData

    init {
        openInitialDestination()
    }

    private fun openInitialDestination() {
        disposables += repository.isAccountSelected()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { isSelected ->
                _removeSplashBackgroundLiveData.value = Event(Unit)

                if (isSelected) {
                    if (repository.isCodeSet()) {
                        router.openInitialCheckPincode()
                    } else {
                        router.openCreatePincode()
                    }
                } else {
                    router.openAddFirstAccount()
                }
            }
    }
}
package jp.co.soramitsu.splash.presentation

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.splash.SplashRouter
import kotlinx.coroutines.launch

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
        viewModelScope.launch {
            if (repository.isAccountSelected()) {
                if (repository.isCodeSet()) {
                    router.openInitialCheckPincode()
                } else {
                    router.openCreatePincode()
                }
            } else {
                router.openAddFirstAccount()
            }

            _removeSplashBackgroundLiveData.value = Event(Unit)
        }
    }
}
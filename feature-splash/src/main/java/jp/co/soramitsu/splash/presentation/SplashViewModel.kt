package jp.co.soramitsu.splash.presentation

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.splash.SplashRouter

class SplashViewModel(
    private val router: SplashRouter
) : ViewModel() {

    private val _openUsersEvent = MutableLiveData<Event<Unit>>()
    val openUsersEvent: LiveData<Event<Unit>> = _openUsersEvent

    init {
        _openUsersEvent.value = Event(Unit)
    }

    fun openScanner(context: Context) {
        router.openMain(context)
    }
}
package jp.co.soramitsu.common.mixin.api

import androidx.lifecycle.LiveData
import io.reactivex.disposables.Disposable

interface NetworkStateMixin : NetworkStateUi {
    val networkStateDisposable: Disposable
}

interface NetworkStateUi {
    val showConnectingBarLiveData: LiveData<Boolean>
}
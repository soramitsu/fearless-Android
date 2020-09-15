package jp.co.soramitsu.common.base

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import jp.co.soramitsu.common.utils.DEFAULT_ERROR_HANDLER
import jp.co.soramitsu.common.utils.ErrorHandler
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.TextProvider
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.common.utils.asMutableLiveData

open class BaseViewModel : ViewModel() {

    private val _errorLiveData = MutableLiveData<Event<TextProvider>>()
    val errorLiveData: LiveData<Event<TextProvider>> = _errorLiveData

    private val _errorWithTitleLiveData = MutableLiveData<Event<Pair<String, String>>>()
    val errorWithTitleLiveData: LiveData<Event<Pair<String, String>>> = _errorWithTitleLiveData

    private val _messageLiveData = MutableLiveData<Event<TextProvider>>()
    val messageLiveData: LiveData<Event<TextProvider>> = _messageLiveData

    protected val disposables = CompositeDisposable()

    override fun onCleared() {
        super.onCleared()
        if (!disposables.isDisposed) disposables.dispose()
    }

    fun showMessage(textProvider: TextProvider) {
        _messageLiveData.value = Event(textProvider)
    }

    fun showError(textProvider: TextProvider) {
        _errorLiveData.value = Event(textProvider)
    }

    fun <T> Single<T>.asLiveData(
        errorHandler: ErrorHandler = DEFAULT_ERROR_HANDLER
    ) = asLiveData(disposables, errorHandler)

    fun <T> Observable<T>.asLiveData(
        errorHandler: ErrorHandler = DEFAULT_ERROR_HANDLER
    ) = asLiveData(disposables, errorHandler)

    fun <T> Single<T>.asMutableLiveData(
        errorHandler: ErrorHandler = DEFAULT_ERROR_HANDLER
    ) = asMutableLiveData(disposables, errorHandler)

    fun <T> Observable<T>.asMutableLiveData(
        errorHandler: ErrorHandler = DEFAULT_ERROR_HANDLER
    ) = asMutableLiveData(disposables, errorHandler)
}
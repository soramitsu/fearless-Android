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
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.common.utils.asMutableLiveData

open class BaseViewModel : ViewModel() {

    private val _errorLiveData = MutableLiveData<Event<String>>()
    val errorLiveData: LiveData<Event<String>> = _errorLiveData

    private val _errorWithTitleLiveData = MutableLiveData<Event<Pair<String, String>>>()
    val errorWithTitleLiveData: LiveData<Event<Pair<String, String>>> = _errorWithTitleLiveData

    private val _messageLiveData = MutableLiveData<Event<String>>()
    val messageLiveData: LiveData<Event<String>> = _messageLiveData

    protected val disposables = CompositeDisposable()

    override fun onCleared() {
        super.onCleared()
        if (!disposables.isDisposed) disposables.dispose()
    }

    fun showMessage(text: String) {
        _messageLiveData.value = Event(text)
    }

    fun showError(title: String, text: String) {
        _errorWithTitleLiveData.value = Event(title to text)
    }

    fun showError(text: String) {
        _errorLiveData.value = Event(text)
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
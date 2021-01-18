package jp.co.soramitsu.common.base

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import jp.co.soramitsu.common.utils.Event

open class BaseViewModel : ViewModel() {

    private val _errorLiveData = MutableLiveData<Event<String>>()
    val errorLiveData: LiveData<Event<String>> = _errorLiveData

    private val _errorWithTitleLiveData = MutableLiveData<Event<Pair<String, String>>>()
    val errorWithTitleLiveData: LiveData<Event<Pair<String, String>>> = _errorWithTitleLiveData

    private val _messageLiveData = MutableLiveData<Event<String>>()
    val messageLiveData: LiveData<Event<String>> = _messageLiveData

    fun showMessage(text: String) {
        _messageLiveData.value = Event(text)
    }

    fun showError(title: String, text: String) {
        _errorWithTitleLiveData.value = Event(title to text)
    }

    fun showError(text: String) {
        _errorLiveData.value = Event(text)
    }

    fun showError(throwable: Throwable) {
        throwable.message?.let(this::showError)
    }
}
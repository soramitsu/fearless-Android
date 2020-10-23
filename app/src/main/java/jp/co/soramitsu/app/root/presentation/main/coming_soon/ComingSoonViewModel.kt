package jp.co.soramitsu.app.root.presentation.main.coming_soon

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.utils.Event

class ComingSoonViewModel(
    private val appLinksProvider: AppLinksProvider
) : BaseViewModel() {

    private val _openBrowserEvent = MutableLiveData<Event<String>>()
    val openBrowserEvent: LiveData<Event<String>> = _openBrowserEvent

    fun roadMapClicked() {
        _openBrowserEvent.value = Event(appLinksProvider.roadMapUrl)
    }

    fun devStatusClicked() {
        _openBrowserEvent.value = Event(appLinksProvider.devStatusUrl)
    }
}
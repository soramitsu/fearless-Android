package jp.co.soramitsu.common.mixin.api

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.utils.Event

interface Browserable {
    val openBrowserEvent: LiveData<Event<String>>

    interface Presentation {
        fun showBrowser(url: String)
    }
}
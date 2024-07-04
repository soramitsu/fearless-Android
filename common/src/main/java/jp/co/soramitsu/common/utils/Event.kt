package jp.co.soramitsu.common.utils

import androidx.lifecycle.Observer
import kotlinx.coroutines.flow.FlowCollector

open class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    fun peekContent(): T = content
}

class EventObserver<T>(private val onEventUnhandledContent: (T) -> Unit) : Observer<Event<T>> {
    override fun onChanged(value: Event<T>) {
        value.getContentIfNotHandled()?.let { content ->
            onEventUnhandledContent(content)
        }
    }
}

class EventCollector<T>(private val onEventUnhandledContent: (T) -> Unit) : FlowCollector<Event<T>> {
    override suspend fun emit(value: Event<T>) {
        value.getContentIfNotHandled()?.let { content ->
            onEventUnhandledContent(content)
        }
    }
}

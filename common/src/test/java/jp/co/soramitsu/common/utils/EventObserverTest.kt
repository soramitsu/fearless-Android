package jp.co.soramitsu.common.utils

import androidx.lifecycle.Observer
import org.junit.Assert.assertEquals
import org.junit.Test

class EventObserverTest {
    @Test fun clearedLiveDataValueDoesNotInvokeContentCallback() {
        val values = mutableListOf<String>()
        val observer = nullableBoundary(EventObserver<String> { values += it })
        observer.onChanged(null)
        assertEquals(emptyList<String>(), values)
    }

    @Test fun clearBetweenEventsDoesNotBlockTheNextDelivery() {
        val values = mutableListOf<String>()
        val observer = nullableBoundary(EventObserver<String> { values += it })
        observer.onChanged(Event("first"))
        observer.onChanged(null)
        observer.onChanged(Event("second"))
        assertEquals(listOf("first", "second"), values)
    }

    @Test fun handledEventIsNotDeliveredTwice() {
        val values = mutableListOf<String>()
        val observer = EventObserver<String> { values += it }
        val event = Event("once")
        observer.onChanged(event)
        observer.onChanged(event)
        assertEquals(listOf("once"), values)
    }

    // LiveData is a Java API and can dispatch null when MutableLiveData.value is cleared.
    // Exercise that actual Observer dispatch boundary without substituting its callback.
    @Suppress("UNCHECKED_CAST")
    private fun nullableBoundary(observer: EventObserver<String>): Observer<Event<String>?> =
        observer as Observer<Event<String>?>
}

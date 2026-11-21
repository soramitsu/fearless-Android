package jp.co.soramitsu.common.utils

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LiveDataTransformationsTest {
    @get:Rule val instant = InstantTaskExecutorRule()

    @Test
    fun shouldMapInitialValuesAndSuppressEqualUpdates() {
        val source = MutableLiveData<Int>()
        val mapped = source.map("waiting") { "value:$it" }
        observe(mapped) { values ->
            assertEquals(listOf("waiting"), values)
            source.setValueIfNew(3)
            source.setValueIfNew(3)
            source.setValueIfNew(4)
            assertEquals(listOf("waiting", "value:3", "value:4"), values)
        }
        assertEquals("value:4", mapped.value)
    }

    @Test
    fun shouldWaitForEverySourceAndRecombineEitherSide() {
        val first = MutableLiveData<Int>()
        val second = MutableLiveData<String>()
        val paired = first.combine(second, initial = "waiting") { a, b -> "$a:$b" }
        observe(paired) { values ->
            first.value = 2
            assertEquals(listOf("waiting"), values)
            second.value = "DOT"
            first.value = 3
            second.value = "KSM"
            assertEquals(listOf("waiting", "2:DOT", "3:DOT", "3:KSM"), values)
        }
        val combined = combine(first, second) { holder -> "${holder.component1<Int>()}/${holder.component2<String>()}" }
        observe(combined) { values -> assertTrue(values.all { it == "3/KSM" }) }
    }

    @Test
    fun shouldMergeSourcesAndAllowMediationOfMissingValues() {
        val first = MutableLiveData<String>()
        val second = MutableLiveData<String>()
        observe(multipleSourceLiveData(first, second)) { values ->
            first.value = "first"
            second.value = "second"
            assertEquals(listOf("first", "second"), values)
        }
        val mediated = mediateWith(first, second) { holder ->
            holder.values.filterIsInstance<String>().takeIf { it.isNotEmpty() }?.joinToString("+")
        }
        observe(mediated) { values ->
            first.value = "changed"
            assertEquals("changed+second", values.last())
        }
    }

    @Test
    fun shouldDetachThePreviousSwitchMapSourceAndAvoidResubscribingToTheSameOne() {
        val selection = MutableLiveData(0)
        val first = MutableLiveData("one")
        val second = MutableLiveData("two")
        val switched = selection.switchMap { if (it == 0) first else second }
        observe(switched) { values ->
            assertEquals("one", values.last())
            selection.value = 1
            assertEquals("two", values.last())
            val size = values.size
            selection.value = 2
            first.value = "stale"
            assertEquals(size, values.size)
            second.value = "fresh"
            assertEquals("fresh", values.last())
        }
        observe(selection.switchMap({ second }, triggerOnSwitch = false)) { values -> assertEquals("fresh", values.last()) }
    }

    @Test
    fun shouldDeliverEventsOnceAndPermitExplicitObserverNotification() {
        val events = MutableLiveData<Event<Unit>>()
        events.sendEvent()
        val event = requireNotNull(events.value)
        assertEquals(Unit, event.getContentIfNotHandled())
        assertNull(event.getContentIfNotHandled())
        val text = MutableLiveData<String>()
        assertFalse(text.isNotEmpty())
        text.value = "ready"
        assertTrue(text.isNotEmpty())
        observe(text.map { it.length }) { values ->
            text.notifyObservers()
            assertEquals(listOf(5, 5), values)
        }
    }

    private fun <T> observe(source: LiveData<T>, check: (MutableList<T>) -> Unit) {
        val values = mutableListOf<T>()
        val observer = Observer<T> { values += it }
        source.observeForever(observer)
        try {
            check(values)
        } finally {
            source.removeObserver(observer)
        }
    }
}

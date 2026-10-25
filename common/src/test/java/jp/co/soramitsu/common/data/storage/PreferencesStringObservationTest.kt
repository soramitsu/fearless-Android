package jp.co.soramitsu.common.data.storage

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

class PreferencesStringObservationTest {
    private val key = "PUBLIC_SELECTION"

    private inner class Store(initial: String? = null) {
        val values = ConcurrentHashMap<String, String>()
        val writes = CopyOnWriteArrayList<Pair<String, String?>>()
        val listeners = CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()
        val storage = mock(SharedPreferences::class.java)
        val preferences = PreferencesImpl(storage)
        var beforeRegister: (() -> Unit)? = null
        var afterContains: (() -> Unit)? = null
        var afterRead: ((String?) -> Unit)? = null

        init {
            if (initial != null) values[key] = initial
            val editor = mock(SharedPreferences.Editor::class.java)
            var pending: Pair<String, String?>? = null
            whenever(storage.contains(any())).thenAnswer {
                val contains = values.containsKey(it.getArgument<String>(0))
                afterContains?.invoke()
                contains
            }
            whenever(storage.getString(any(), anyOrNull())).thenAnswer {
                val value = values[it.getArgument<String>(0)] ?: it.getArgument<String?>(1)
                afterRead?.invoke(value)
                value
            }
            whenever(storage.edit()).thenReturn(editor)
            whenever(editor.putString(any(), anyOrNull())).thenAnswer {
                pending = it.getArgument<String>(0) to it.getArgument<String?>(1)
                editor
            }
            doAnswer {
                val (field, value) = checkNotNull(pending)
                writes += field to value
                write(field, value)
                null
            }.whenever(editor).apply()
            doAnswer {
                beforeRegister?.invoke()
                listeners += it.getArgument<SharedPreferences.OnSharedPreferenceChangeListener>(0)
                null
            }.whenever(storage).registerOnSharedPreferenceChangeListener(any())
            doAnswer {
                listeners -= it.getArgument<SharedPreferences.OnSharedPreferenceChangeListener>(0)
                null
            }.whenever(storage).unregisterOnSharedPreferenceChangeListener(any())
        }

        fun write(field: String, value: String?) {
            if (value == null) values.remove(field) else values[field] = value
            listeners.forEach { it.onSharedPreferenceChanged(storage, field) }
        }
    }

    @Test
    fun absentNoInitializerEmitsNullWithoutWritingOrCreatingKey() = runBlocking {
        val store = Store()
        assertNull(withTimeout(1000) { store.preferences.stringFlow(key).first() })
        assertTrue(store.values.isEmpty())
        assertTrue(store.writes.isEmpty())
        assertTrue(store.listeners.isEmpty())
    }

    @Test
    fun existingNoInitializerEmitsExactValueWithoutWrites() = runBlocking {
        val store = Store("chain:token:42")
        assertEquals("chain:token:42", withTimeout(1000) { store.preferences.stringFlow(key).first() })
        assertTrue(store.writes.isEmpty())
        assertTrue(store.listeners.isEmpty())
    }

    @Test
    fun explicitUpdateDuringRegistrationIsObservedWithoutOverwrite() = runBlocking {
        val store = Store()
        store.beforeRegister = { store.write(key, "explicit:42") }
        assertEquals("explicit:42", withTimeout(1000) { store.preferences.stringFlow(key).first() })
        assertEquals("explicit:42", store.values[key])
        assertTrue(store.writes.isEmpty())
        assertTrue(store.listeners.isEmpty())
    }

    @Test
    fun explicitChoiceCreatedAtFirstObservationCannotBeDeletedByNullInitialization() = runBlocking {
        val store = Store()
        val inserted = AtomicBoolean(false)
        val explicitWrite = {
            if (inserted.compareAndSet(false, true)) store.write(key, "explicit:42")
            Unit
        }
        // On the old path the write lands after contains(false); on the observation
        // path it lands before registration completes. Neither permits deletion.
        store.afterContains = explicitWrite
        store.beforeRegister = explicitWrite
        assertEquals("explicit:42", withTimeout(1000) { store.preferences.stringFlow(key).first() })
        assertTrue(inserted.get())
        assertEquals("explicit:42", store.values[key])
        assertTrue(store.writes.isEmpty())
        assertTrue(store.listeners.isEmpty())
    }

    @Test
    fun concurrentUpdateDuringInitialReadCannotBeLostOrOvertakenByOldSnapshot() = runBlocking {
        val store = Store("old:0")
        val readStarted = CountDownLatch(1)
        val valueChanged = CountDownLatch(1)
        val threadFailure = AtomicReference<Throwable?>()
        store.afterRead = {
            store.afterRead = null
            readStarted.countDown()
            check(valueChanged.await(2, TimeUnit.SECONDS))
        }
        val writer = thread(name = "public-preference-update") {
            try {
                check(readStarted.await(2, TimeUnit.SECONDS))
                store.values[key] = "new:1"
                valueChanged.countDown()
                store.listeners.forEach { it.onSharedPreferenceChanged(store.storage, key) }
            } catch (failure: Throwable) {
                threadFailure.set(failure)
                valueChanged.countDown()
            }
        }
        val observations = CopyOnWriteArrayList<String?>()
        val job = launch(Dispatchers.Default) {
            store.preferences.stringFlow(key).distinctUntilChanged().collect { observations += it }
        }
        try {
            withTimeout(1000) { while (observations.lastOrNull() != "new:1") delay(5) }
            writer.join(2000)
            assertTrue(!writer.isAlive)
            assertNull(threadFailure.get())
            assertTrue(observations.toList() in listOf(listOf("old:0", "new:1"), listOf("new:1")))
            assertEquals("new:1", store.values[key])
            assertTrue(store.writes.isEmpty())
        } finally {
            job.cancelAndJoin()
            writer.join(2000)
        }
        assertTrue(store.listeners.isEmpty())
    }

    @Test
    fun removalEmitsNullAndUnrelatedChangeDoesNotEmit() = runBlocking {
        val store = Store("selected:0")
        val observations = CopyOnWriteArrayList<String?>()
        val job = launch { store.preferences.stringFlow(key).collect { observations += it } }
        try {
            withTimeout(1000) { while (observations.isEmpty()) delay(5) }
            store.write("unrelated", "retained")
            delay(30)
            assertEquals(listOf("selected:0"), observations.toList())
            store.write(key, null)
            withTimeout(1000) { while (observations.size != 2) delay(5) }
            assertEquals(listOf("selected:0", null), observations.toList())
            assertEquals(mapOf("unrelated" to "retained"), store.values)
            assertTrue(store.writes.isEmpty())
        } finally {
            job.cancelAndJoin()
        }
        assertTrue(store.listeners.isEmpty())
    }

    @Test
    fun initialReadFailurePropagatesAndUnregistersListener() = runBlocking {
        val store = Store("selected:0")
        val failure = AssertionError("public initial observation failure")
        store.afterRead = { throw failure }
        val observed = try {
            withTimeout(1000) { store.preferences.stringFlow(key).first() }
            null
        } catch (caught: Throwable) {
            caught
        }
        // Coroutine stacktrace recovery may copy the exception and retain its origin as cause.
        val origin = if (observed !== failure && observed != null &&
            observed.javaClass == failure.javaClass && observed.message == failure.message
        ) observed.cause else observed
        assertSame(failure, origin)
        assertTrue(store.listeners.isEmpty())
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun producerPresentStillInitializesAbsentPreferenceExactlyOnce() = runBlocking {
        val store = Store()
        var calls = 0
        val result = withTimeout(1000) {
            store.preferences.stringFlow(key) {
                calls++
                "produced:42"
            }.first()
        }
        assertEquals("produced:42", result)
        assertEquals(1, calls)
        assertEquals(listOf(key to "produced:42"), store.writes.toList())
        assertEquals("produced:42", store.values[key])
        assertTrue(store.listeners.isEmpty())
    }

    @Test
    fun producerPresentDoesNotReplaceExistingChoice() = runBlocking {
        val store = Store("explicit:42")
        var calls = 0
        val result = withTimeout(1000) {
            store.preferences.stringFlow(key) {
                calls++
                "unused:0"
            }.first()
        }
        assertEquals("explicit:42", result)
        assertEquals(0, calls)
        assertTrue(store.writes.isEmpty())
        assertTrue(store.listeners.isEmpty())
    }

    @Test
    fun slowCollectorEventuallyObservesFinalExplicitChoiceAfterBurst() = runBlocking {
        val store = Store("initial:0")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val observations = CopyOnWriteArrayList<String?>()
        val job = launch {
            store.preferences.stringFlow(key).collect { value ->
                observations += value
                if (value == "initial:0") {
                    entered.complete(Unit)
                    release.await()
                }
            }
        }
        try {
            withTimeout(1000) { entered.await() }
            assertEquals(1, store.listeners.size)
            repeat(100) { index -> store.write(key, "explicit:${index + 1}") }
            release.complete(Unit)
            withTimeout(1000) { while (observations.lastOrNull() != "explicit:100") delay(5) }
            assertEquals("initial:0", observations.first())
            assertEquals("explicit:100", observations.last())
            assertEquals("explicit:100", store.values[key])
            assertTrue(store.writes.isEmpty())
        } finally {
            release.complete(Unit)
            job.cancelAndJoin()
        }
        assertTrue(store.listeners.isEmpty())
    }

    @Test
    fun nullKeyClearNotificationRereadsSelectionWithoutWriting() = runBlocking {
        val store = Store("explicit:42")
        val observations = CopyOnWriteArrayList<String?>()
        val job = launch { store.preferences.stringFlow(key).collect { observations += it } }
        try {
            withTimeout(1000) { while (observations.isEmpty()) delay(5) }
            store.values.clear()
            store.listeners.forEach { it.onSharedPreferenceChanged(store.storage, null) }
            withTimeout(1000) { while (observations.size != 2) delay(5) }
            assertEquals(listOf("explicit:42", null), observations.toList())
            assertTrue(store.values.isEmpty())
            assertTrue(store.writes.isEmpty())
        } finally {
            job.cancelAndJoin()
        }
        assertTrue(store.listeners.isEmpty())
    }
}

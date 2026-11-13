package jp.co.soramitsu.common.data.storage

import android.content.SharedPreferences
import jp.co.soramitsu.common.data.network.nomis.NOMIS_SCORING_ENABLED_KEY
import jp.co.soramitsu.common.data.network.nomis.NomisApi
import jp.co.soramitsu.common.data.network.nomis.NomisResponse
import jp.co.soramitsu.common.data.network.nomis.PreferenceAwareNomisApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class PreferencesBooleanObservationTest {
    private val key = NOMIS_SCORING_ENABLED_KEY

    private inner class Store(initial: Boolean? = null) {
        val values = ConcurrentHashMap<String, Boolean>()
        val writes = CopyOnWriteArrayList<Boolean>()
        val listeners = CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()
        val storage = mock(SharedPreferences::class.java)
        val preferences = PreferencesImpl(storage)
        var beforeRegister: (() -> Unit)? = null
        var afterContains: (() -> Unit)? = null
        var afterRead: (() -> Unit)? = null

        init {
            if (initial != null) values[key] = initial
            val editor = mock(SharedPreferences.Editor::class.java)
            var pending = false
            whenever(storage.contains(any())).thenAnswer {
                val present = values.containsKey(it.getArgument<String>(0))
                afterContains?.invoke()
                present
            }
            whenever(storage.getBoolean(any(), any())).thenAnswer {
                val value = values[it.getArgument<String>(0)] ?: it.getArgument<Boolean>(1)
                afterRead?.invoke()
                value
            }
            whenever(storage.edit()).thenReturn(editor)
            whenever(editor.putBoolean(any(), any())).thenAnswer {
                pending = it.getArgument(1)
                editor
            }
            doAnswer {
                writes += pending
                write(pending)
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

        fun write(value: Boolean) {
            values[key] = value
            listeners.forEach { it.onSharedPreferenceChanged(storage, key) }
        }
    }

    @Test
    fun observingAbsentPreferenceUsesDefaultWithoutWriting() = runBlocking {
        for (default in listOf(false, true)) {
            val store = Store()
            assertEquals(default, withTimeout(1000) { store.preferences.booleanFlow(key, default).first() })
            assertTrue(store.values.isEmpty())
            assertTrue(store.writes.isEmpty())
            assertTrue(store.listeners.isEmpty())
        }
    }

    @Test
    fun optOutDuringListenerRegistrationIsObserved() = runBlocking {
        val store = Store(true)
        store.beforeRegister = { store.write(false) }
        assertFalse(withTimeout(1000) { store.preferences.booleanFlow(key, true).first() })
        assertEquals(false, store.values[key])
        assertTrue(store.writes.isEmpty())
        assertTrue(store.listeners.isEmpty())
    }

    @Test
    fun observingAbsentPreferenceCannotOverwriteConcurrentOptOut() = runBlocking {
        val store = Store()
        store.afterContains = { store.write(false) }
        store.beforeRegister = { store.write(false) }
        assertFalse(withTimeout(1000) { store.preferences.booleanFlow(key, true).first() })
        assertEquals(false, store.values[key])
        assertTrue(store.writes.isEmpty())
        assertTrue(store.listeners.isEmpty())
    }

    @Test
    fun concurrentOptOutCannotBeOvertakenByOlderInitialSnapshot() = runBlocking {
        val store = Store(true)
        val readStarted = CountDownLatch(1)
        val valueChanged = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        store.afterRead = {
            store.afterRead = null
            readStarted.countDown()
            check(valueChanged.await(2, TimeUnit.SECONDS))
        }
        val writer = thread(name = "preference-opt-out") {
            try {
                check(readStarted.await(2, TimeUnit.SECONDS))
                store.values[key] = false
                valueChanged.countDown()
                store.listeners.forEach { it.onSharedPreferenceChanged(store.storage, key) }
            } catch (caught: Throwable) {
                failure.set(caught)
                valueChanged.countDown()
            }
        }
        val observations = CopyOnWriteArrayList<Boolean>()
        val observer = launch(Dispatchers.Default) {
            store.preferences.booleanFlow(key, true).distinctUntilChanged().collect { observations += it }
        }
        try {
            withTimeout(1000) { while (observations.lastOrNull() != false) delay(5) }
            writer.join(2000)
            assertFalse(writer.isAlive)
            assertNull(failure.get())
            assertTrue(observations.toList() in listOf(listOf(true, false), listOf(false)))
            assertTrue(store.writes.isEmpty())
        } finally {
            observer.cancelAndJoin()
            writer.join(2000)
        }
        assertTrue(store.listeners.isEmpty())
    }

    @Test
    fun clearNotificationRereadsDefaultAndCancellationRemovesListener() = runBlocking {
        val store = Store(true)
        val observations = CopyOnWriteArrayList<Boolean>()
        val observer = launch { store.preferences.booleanFlow(key, false).collect { observations += it } }
        try {
            withTimeout(1000) { while (observations.isEmpty()) delay(5) }
            store.values.clear()
            store.listeners.forEach { it.onSharedPreferenceChanged(store.storage, null) }
            withTimeout(1000) { while (observations.lastOrNull() != false) delay(5) }
            assertEquals(listOf(true, false), observations.toList())
            assertTrue(store.values.isEmpty())
            assertTrue(store.writes.isEmpty())
        } finally {
            observer.cancelAndJoin()
        }
        assertTrue(store.listeners.isEmpty())
    }

    @Test
    fun realPreferenceOptOutCancelsProviderRequestAndRemovesListener() = runBlocking {
        val store = Store(true)
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val api = PreferenceAwareNomisApi(
            object : NomisApi {
                override suspend fun getNomisScore(address: String): NomisResponse {
                    entered.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
            },
            store.preferences
        )
        val request = async { api.getNomisScore("synthetic-wallet-address") }
        try {
            withTimeout(1000) {
                entered.await()
                while (store.listeners.isEmpty()) delay(5)
                store.write(false)
                cancelled.await()
                request.join()
            }
            assertTrue(request.isCancelled)
            assertTrue(store.listeners.isEmpty())
            assertTrue(store.writes.isEmpty())
        } finally {
            request.cancelAndJoin()
        }
    }
}

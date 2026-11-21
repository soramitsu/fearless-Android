package jp.co.soramitsu.tonconnect.api.model

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class OkHttpClientSseTest {

    @Test
    fun shouldOmitLastEventId_whenNoEventWasRecorded() {
        assertNull(sseRequest(URL, null).header("Last-Event-ID"))
        assertNull(sseRequest(URL, 0).header("Last-Event-ID"))
        assertEquals("text/event-stream", sseRequest(URL, null).header("Accept"))
    }

    @Test
    fun shouldSendTheRecordedLastEventId() {
        assertEquals("1700000000123456", sseRequest(URL, 1_700_000_000_123_456L).header("Last-Event-ID"))
    }

    @Test
    fun shouldResumeAfterTheLatestRecordedEvent_whenItReconnectsAfterAFailure() = runBlocking {
        val lastEventIds = ArrayDeque(listOf<Long?>(null, 42L))
        val factory = ScriptedFactory(
            { listener -> listener.onFailure(this, IOException("connection reset"), null) },
            { listener -> listener.onEvent(this, "43", "message", "{}") }
        )

        val event = withTimeout(TIMEOUT_MILLIS) {
            factory.sse(URL, { lastEventIds.removeFirst() }, retryDelayMillis = 0).first()
        }

        assertEquals("43", event.id)
        assertEquals(listOf(null, "42"), factory.requests.map { it.header("Last-Event-ID") })
    }

    @Test
    fun shouldReconnect_whenTheServerClosesTheStream() = runBlocking {
        val factory = ScriptedFactory(
            { listener -> listener.onClosed(this) },
            { listener -> listener.onEvent(this, "7", "message", "{}") }
        )

        val event = withTimeout(TIMEOUT_MILLIS) {
            factory.sse(URL, { 6L }, retryDelayMillis = 0).first()
        }

        assertEquals("7", event.id)
        assertEquals(listOf("6", "6"), factory.requests.map { it.header("Last-Event-ID") })
    }

    /** Plays one scripted reaction per connection attempt. */
    private class ScriptedFactory(
        vararg scriptedAttempts: EventSource.(EventSourceListener) -> Unit
    ) : EventSource.Factory {
        private val attempts = ArrayDeque(scriptedAttempts.toList())
        val requests = mutableListOf<Request>()

        override fun newEventSource(request: Request, listener: EventSourceListener): EventSource {
            requests += request
            val source = object : EventSource {
                override fun request(): Request = request
                override fun cancel() = Unit
            }
            attempts.removeFirstOrNull()?.invoke(source, listener)
            return source
        }
    }

    private companion object {
        const val URL = "https://bridge.example.com/bridge/events?client_id=abc"
        const val TIMEOUT_MILLIS = 10_000L
    }
}

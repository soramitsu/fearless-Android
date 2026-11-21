package jp.co.soramitsu.tonconnect.api.model

import android.util.ArrayMap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.retry
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

private const val SSE_RETRY_DELAY_MILLIS = 1000L
private const val LAST_EVENT_ID_HEADER = "Last-Event-ID"

private fun requestBuilder(url: String): Request.Builder {
    val builder = Request.Builder()
    builder.url(url)
    return builder
}

fun OkHttpClient.post(
    url: String,
    body: RequestBody,
    headers: ArrayMap<String, String>? = null
): Response {
    val builder = requestBuilder(url)
    builder.post(body)
    headers?.forEach { (key, value) ->
        builder.addHeader(key, value)
    }
    return execute(builder.build())
}

private fun OkHttpClient.execute(request: Request): Response {
    val response = newCall(request).execute()
    if (!response.isSuccessful) {
        throw OkHttpError(response)
    }
    return response
}

class OkHttpError(
    private val response: Response
) : Exception("HTTP error: ${response.code}")

/** Raised when the server ends an event stream, so that the subscription reconnects. */
class EventStreamClosedException : Exception("The event stream was closed by the server")

fun OkHttpClient.sseFactory() = EventSources.createFactory(this)

/**
 * Subscribes to a server-sent event stream and reconnects after a network
 * failure or a server-side close. [lastEventId] is read again for every
 * connection attempt, so a reconnect resumes after the last event the caller
 * has recorded instead of replaying the server's queue.
 */
fun OkHttpClient.sse(url: String, lastEventId: suspend () -> Long?): Flow<SSEvent> = sseFactory().sse(url, lastEventId)

internal fun EventSource.Factory.sse(
    url: String,
    lastEventId: suspend () -> Long?,
    retryDelayMillis: Long = SSE_RETRY_DELAY_MILLIS
): Flow<SSEvent> = callbackFlow {
    val listener = object : EventSourceListener() {
        override fun onEvent(
            eventSource: EventSource,
            id: String?,
            type: String?,
            data: String
        ) {
            this@callbackFlow.trySendBlocking(SSEvent(id, type, data))
        }

        override fun onFailure(
            eventSource: EventSource,
            t: Throwable?,
            response: Response?
        ) {
            t?.printStackTrace()
            this@callbackFlow.close(t)
        }

        override fun onClosed(eventSource: EventSource) {
            // Like a browser EventSource, resume after a server-side close.
            this@callbackFlow.close(EventStreamClosedException())
        }
    }
    val request = sseRequest(url, lastEventId())
    val events = newEventSource(request, listener)
    awaitClose { events.cancel() }
}.cancellable().retry { _ ->
    delay(retryDelayMillis)
    true
}

internal fun sseRequest(url: String, lastEventId: Long?): Request {
    val builder = requestBuilder(url)
        .addHeader("Accept", "text/event-stream")
        .addHeader("Cache-Control", "no-cache")
        .addHeader("Connection", "keep-alive")

    // Without a recorded event the header is left out rather than sent as 0.
    if (lastEventId != null && lastEventId > 0) {
        builder.addHeader(LAST_EVENT_ID_HEADER, lastEventId.toString())
    }
    return builder.build()
}

package co.jp.soramitsu.tonconnect.model

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


fun OkHttpClient.sseFactory() = EventSources.createFactory(this)

fun OkHttpClient.sse(
    url: String,
    lastEventId: Long? = null
): Flow<SSEvent> = callbackFlow {
    val listener = object : EventSourceListener() {
        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            println("!!! OkHttpClient.sse listener onEvent: eventSource = $eventSource; id = $id; type = $type; data = $data")
            this@callbackFlow.trySendBlocking(SSEvent(id, type, data))
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            println("!!! OkHttpClient.sse listener onFailure: eventSource = $eventSource; response = $response; t = ${t?.message}")
            this@callbackFlow.close(t)
        }

        override fun onClosed(eventSource: EventSource) {
            println("!!! OkHttpClient.sse listener onClosed: eventSource = $eventSource")
            this@callbackFlow.close()
        }

        override fun onOpen(eventSource: EventSource, response: Response) {
            println("!!! OkHttpClient.sse listener onOpen: eventSource = $eventSource; response = $response")
            super.onOpen(eventSource, response)
        }
    }
    val builder = requestBuilder(url)
        .addHeader("Accept", "text/event-stream")
        .addHeader("Cache-Control", "no-cache")
        .addHeader("Connection", "keep-alive")

    if (lastEventId != null) {
        builder.addHeader("Last-Event-ID", lastEventId.toString())
    }
    val request = builder.build()
    val events = sseFactory().newEventSource(request, listener)
    awaitClose { events.cancel() }
}.cancellable().retry { _ ->
    delay(1000)
    true
}.cancellable()

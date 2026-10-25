package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage

import jp.co.soramitsu.fearless_utils.any
import jp.co.soramitsu.fearless_utils.argThat
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import org.junit.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.verify
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.times

class StorageSubscriptionMultiplexerTest {

    private val callbacks = mutableMapOf<String, MutableList<MultiplexerCallback>>()

    @Test
    fun `should deliver callback only to affected subscribers`() {
        putCallback("1", createCallback())
        putCallback("2", createCallback())
        putCallback("3", createCallback())

        val multiplexer = StorageSubscriptionMultiplexer.Builder()
            .subscribe("1", callbacksForKey("1").first())
            .subscribe("2", callbacksForKey("2").first())
            .subscribe("3", callbacksForKey("3").first())
            .build()

        multiplexer.onNext(createResponse("1", "3"))

        verifyCalledWithValidParams("1")
        verifyCalledWithValidParams("3")
    }

    @Test
    fun `should deliver callback to several subscribers for key`() {
        putCallback("1", createCallback())
        putCallback("1", createCallback())
        putCallback("2", createCallback())

        val multiplexer = StorageSubscriptionMultiplexer.Builder()
            .subscribe("1", callbacksForKey("1").first())
            .subscribe("1", callbacksForKey("1")[1])
            .subscribe("2", callbacksForKey("2").first())
            .build()

        multiplexer.onNext(createResponse("1", "3"))

        verifyCalledWithValidParams("1")
    }

    @Test
    fun `should deliver error to all subscribers`() {
        putCallback("1", createCallback())
        putCallback("1", createCallback())
        putCallback("2", createCallback())

        val multiplexer = StorageSubscriptionMultiplexer.Builder()
            .subscribe("1", callbacksForKey("1").first())
            .subscribe("1", callbacksForKey("1")[1])
            .subscribe("2", callbacksForKey("2").first())
            .build()

        multiplexer.onError(Exception())

        verifyCalledWithError("1")
        verifyCalledWithError("2")
    }

    private fun verifyCalledWithValidParams(key: String) {
        val callbacksForKey = callbacks[key]!!

        callbacksForKey.forEach {
            verify(it, times(1)).onNext(argThat { change -> change.key == key && change.value == key })
        }
    }

    private fun callbacksForKey(key: String) = callbacks[key]!!

    private fun verifyCalledWithError(key: String) {
        val callbacksForKey = callbacks[key]!!

        callbacksForKey.forEach {
            Mockito.verify(it, times(1)).onError(any())
        }
    }

    private fun createResponse(vararg affectedKeys: String): SubscriptionChange {
        val result = mapOf(
            "block" to "1",
            "changes" to affectedKeys.map { listOf(it, it) }
        )

        val paramsMock = mock(SubscriptionChange.Params::class.java)
        given(paramsMock.result).willReturn(result)

        val changeMock = mock(SubscriptionChange::class.java)
        given(changeMock.params).willReturn(paramsMock)

        return changeMock
    }

    @Suppress("UNCHECKED_CAST")
    private fun createCallback(): MultiplexerCallback {
        return mock(SocketService.ResponseListener::class.java) as MultiplexerCallback
    }

    private fun putCallback(key: String, callback: MultiplexerCallback) {
        val forKey = callbacks.getOrPut(key) { mutableListOf() }

        forKey.add(callback)
    }
}
package jp.co.soramitsu.core.rpc

import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Watching a submitted extrinsic uses author_submitAndWatchExtrinsic. Its unsubscribe method was derived by the
 * "subscribe* -> unsubscribe*" rule, which rejects this method name, so extrinsicStatusFlow() threw
 * IllegalArgumentException before it listened and ExtrinsicService.submitAndWatchExtrinsic always gave up.
 */
class RpcCallsExtrinsicStatusTest {

    @Test
    fun `watching an extrinsic listens with the author watch methods`() = runBlocking {
        val update = SubscriptionChange("2.0", "author_extrinsicUpdate", SubscriptionChange.Params(mapOf(RpcCalls.IN_BLOCK to "0x01"), "watch-1"))
        val requests = mutableListOf<Pair<RuntimeRequest, String>>()
        val socket = mock<SocketService>()
        whenever(socket.subscribe(any(), any(), any())).thenAnswer { call ->
            requests += call.getArgument<RuntimeRequest>(0) to call.getArgument<String>(2)
            call.getArgument<SocketService.ResponseListener<SubscriptionChange>>(1).onNext(update)
            mock<SocketService.Cancellable>()
        }
        val connection = mock<ChainConnection> { on { socketService } doReturn socket }
        val registry = mock<IChainRegistry> { on { getConnection("chain") } doReturn connection }

        val first = withTimeout(10_000) { RpcCalls(registry).extrinsicStatusFlow("chain", "0xsigned").first() }

        assertSame(update, first)
        assertEquals(1, requests.size)
        assertEquals("author_submitAndWatchExtrinsic", requests.single().first.method)
        assertEquals(listOf("0xsigned"), requests.single().first.params)
        assertEquals("author_unwatchExtrinsic", requests.single().second)
    }
}

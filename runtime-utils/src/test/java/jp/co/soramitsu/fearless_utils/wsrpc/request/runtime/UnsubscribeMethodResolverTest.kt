package jp.co.soramitsu.fearless_utils.wsrpc.request.runtime

import jp.co.soramitsu.fearless_utils.common.assertThrows
import org.junit.Assert.*
import org.junit.Test
import java.lang.IllegalArgumentException

class UnsubscribeMethodResolverTest {

    @Test
    fun `should resolve storage subscription`() {
        performTest("state_subscribeStorage", "state_unsubscribeStorage")
    }

    @Test
    fun `should resolve runtime version subscription`() {
        performTest("state_subscribeRuntimeVersion", "state_unsubscribeRuntimeVersion")
    }

    @Test
    fun `should resolve call chain group`() {
        performTest("chain_subscribeAllHeads", "chain_unsubscribeAllHeads")
    }

    @Test
    fun `should throw on non-subscribe method`() {
        assertThrows<IllegalArgumentException> {
            UnsubscribeMethodResolver.resolve("state_getStorage")
        }
    }

    private fun performTest(subscribeName: String, expectedUnsubscribeName : String) {
        assertEquals(expectedUnsubscribeName, UnsubscribeMethodResolver.resolve(subscribeName))
    }
}
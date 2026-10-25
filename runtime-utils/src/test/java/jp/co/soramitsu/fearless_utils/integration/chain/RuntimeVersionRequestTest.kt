package jp.co.soramitsu.fearless_utils.integration.chain

import jp.co.soramitsu.fearless_utils.integration.BaseIntegrationTest
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersionRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.SubscribeRuntimeVersionRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.runtimeVersionChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
@Ignore("Manual run only")
class RuntimeVersionRequestTest : BaseIntegrationTest() {

    @Test
    fun `should fetch runtime version`() = runBlocking {
        val request = RuntimeVersionRequest()

        val result = socketService.executeAsync(request)

        print(result)

        assert(result.result is Map<*, *>)
    }

    @Test
    fun `should subscribe runtime version`() = runBlocking {
        val request = SubscribeRuntimeVersionRequest

        val result = socketService.subscriptionFlow(request).first()

        print(result.runtimeVersionChange().specVersion)
    }
}

package jp.co.soramitsu.fearless_utils.integration.system

import jp.co.soramitsu.fearless_utils.integration.BaseIntegrationTest
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.system.NodeNetworkTypeRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
@Ignore("Manual run only")
class NodeNetworkTypeRequestTest : BaseIntegrationTest() {

    @Test
    fun `should get node network type`() = runBlocking {
        val response = socketService.executeAsync(NodeNetworkTypeRequest())

        val type = response.result as String

        assertEquals("Kusama", type)
    }
}

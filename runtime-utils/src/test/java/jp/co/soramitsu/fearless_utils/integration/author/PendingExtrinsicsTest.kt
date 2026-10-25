package jp.co.soramitsu.fearless_utils.integration.author

import jp.co.soramitsu.fearless_utils.integration.BaseIntegrationTest
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.author.PendingExtrinsicsRequest
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
@Ignore("Manual run only")
class PendingExtrinsicsTest : BaseIntegrationTest() {

    @Test
    fun `should get pending extrinsics`() = runBlocking {
        val request = PendingExtrinsicsRequest()

        val result = socketService.executeAsync(request)

        print(result)

        assert(result.result is List<*>)
    }
}

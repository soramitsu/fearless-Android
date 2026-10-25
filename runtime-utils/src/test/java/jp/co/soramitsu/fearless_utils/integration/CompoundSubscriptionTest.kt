package jp.co.soramitsu.fearless_utils.integration

import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.StorageSubscriptionMultiplexer
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.subscribeUsing
import jp.co.soramitsu.fearless_utils.wsrpc.subscribe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test

@Ignore("Manual run only")
class CompoundSubscriptionTest : BaseIntegrationTest() {

    @Test
    fun `should subscribe multiple keys`() = runBlocking {
        val builder = StorageSubscriptionMultiplexer.Builder()

        val oneFlow = builder.subscribe("0x5f3e4907f716ac89b6347d15ececedca487df464e44a534ba6b0cbb32407b587")
        val twoFlow = builder.subscribe("0x01")

        socketService.subscribeUsing(builder.build())

        println(oneFlow.first().value)
        println(twoFlow.first().value)
    }
}
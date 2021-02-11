package jp.co.soramitsu.core_db.prepopulate.runtime

import jp.co.soramitsu.fearless_utils.runtime.metadata.GetMetadataRequest
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.test_shared.createTestSocket
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test

@Ignore("Manual run only")
class GetCurrentRuntime {

    @Test
    fun getKusamaRuntime() {
        printRuntime("kusama-rpc")
    }

    @Test
    fun getPolkadotRuntime() = runBlocking {
        printRuntime("rpc")
    }


    @Test
    fun getWestendRuntime() {
        printRuntime("westend-rpc")
    }

    private fun printRuntime(subDomain: String) = runBlocking {
        val socketService = createTestSocket()

        val url = "wss://$subDomain.polkadot.io"
        socketService.start(url)

        val runtime = socketService.executeAsync(GetMetadataRequest, mapper = pojo<String>().nonNull())

        print(runtime)
    }
}
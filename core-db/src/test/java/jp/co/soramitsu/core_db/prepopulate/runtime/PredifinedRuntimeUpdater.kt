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
        printRuntimeForParityNode("kusama-rpc")
    }

    @Test
    fun getPolkadotRuntime() = runBlocking {
        printRuntimeForParityNode("rpc")
    }


    @Test
    fun getWestendRuntime() {
        printRuntimeForParityNode("westend-rpc")
    }
    @Test
    fun getRococoWestedRuntime() {
        printRuntime("wss://rococo-community-rpc.laminar.codes/ws")
    }

    private fun printRuntimeForParityNode(subdomain: String) = printRuntime("wss://$subdomain.polkadot.io")

    private fun printRuntime(url: String) = runBlocking {
        val socketService = createTestSocket()

        socketService.start(url)

        val runtime = socketService.executeAsync(GetMetadataRequest, mapper = pojo<String>().nonNull())

        print(runtime)
    }
}

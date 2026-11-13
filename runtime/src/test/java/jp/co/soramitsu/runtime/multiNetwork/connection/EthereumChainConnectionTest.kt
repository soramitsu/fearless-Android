package jp.co.soramitsu.runtime.multiNetwork.connection

import io.mockk.every
import io.mockk.mockk
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class EthereumChainConnectionTest {

    private fun connectionTo(url: String) = EthereumChainConnection(
        chain = mockk<Chain> {
            every { id } returns "evm-network"
            every { name } returns "EVM network"
            every { isEthereumChain } returns true
            every { nodes } returns listOf(ChainNode(url = url, name = "node", isActive = true, isDefault = true))
        },
        onSelectedNodeChange = { _, _ -> },
        allNodesHaveFailed = {}
    )

    @Test
    fun `a new connection hands out its client once a node has answered`() = runBlocking {
        JsonRpcNode().use { node ->
            val connection = connectionTo(node.url)
            assertNull("no client before the first attempt", connection.web3j)

            val client = withTimeout(TEST_LIMIT_MILLIS) { connection.awaitWeb3j() }

            assertSame(connection.web3j, client)
        }
    }

    @Test
    fun `no client is handed out while no node answers`() = runBlocking {
        val connection = connectionTo("http://127.0.0.1:1/")

        assertNull(withTimeoutOrNull(NO_NODE_LIMIT_MILLIS) { connection.awaitWeb3j() })
    }

    /** Answers every HTTP request with one JSON-RPC result, as a node answers eth_blockNumber. */
    private class JsonRpcNode : Closeable {
        private val socket = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        val url = "http://127.0.0.1:${socket.localPort}/"

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    runCatching { socket.accept().use(::answer) }
                }
            }
        }

        private fun answer(client: Socket) {
            val input = client.getInputStream().bufferedReader()
            var contentLength = 0
            while (true) {
                val line = input.readLine()
                if (line.isNullOrEmpty()) break
                if (line.startsWith("Content-Length", ignoreCase = true)) {
                    contentLength = line.substringAfter(':').trim().toInt()
                }
            }
            repeat(contentLength) { input.read() }
            val body = """{"jsonrpc":"2.0","id":1,"result":"0x1"}"""
            val head = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n"
            client.getOutputStream().write((head + body).toByteArray())
        }

        override fun close() = socket.close()
    }

    private companion object {
        const val TEST_LIMIT_MILLIS = 30_000L
        const val NO_NODE_LIMIT_MILLIS = 2_500L
    }
}

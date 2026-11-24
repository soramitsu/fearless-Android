package jp.co.soramitsu.runtime.multiNetwork.runtime.metadatahash

import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RpcSigningMetadataRemoteTest {

    private val requests = mutableListOf<RuntimeRequest>()

    private fun socket(result: Any?): SocketService {
        val socket = mockk<SocketService>()
        every { socket.jsonMapper } returns Gson()
        val request = slot<RuntimeRequest>()
        val listener = slot<SocketService.ResponseListener<RpcResponse>>()
        every { socket.executeRequest(capture(request), any(), capture(listener)) } answers {
            requests += request.captured
            listener.captured.onNext(RpcResponse("2.0", result, request.captured.id, null))
            mockk(relaxed = true)
        }
        return socket
    }

    @Test
    fun `asks for V15 metadata and unwraps the option`() = runBlocking {
        // Some(vec![1, 2, 3])
        val metadata = RpcSigningMetadataRemote { socket("0x010c010203") }.metadataV15("chain")
        assertArrayEquals(byteArrayOf(1, 2, 3), metadata)
        assertEquals("state_call", requests.single().method)
        assertEquals(listOf("Metadata_metadata_at_version", "0x0f000000"), requests.single().params)
    }

    @Test
    fun `a runtime without V15 answers None`() = runBlocking {
        assertNull(RpcSigningMetadataRemote { socket("0x00") }.metadataV15("chain"))
    }

    @Test
    fun `reads system properties`() = runBlocking {
        val properties = RpcSigningMetadataRemote { socket(mapOf("tokenDecimals" to 10.0, "tokenSymbol" to "DOT")) }.systemProperties("chain")
        assertEquals("DOT", properties["tokenSymbol"])
        assertEquals("system_properties", requests.single().method)
    }
}

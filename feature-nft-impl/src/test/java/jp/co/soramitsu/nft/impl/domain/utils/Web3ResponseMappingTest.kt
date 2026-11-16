package jp.co.soramitsu.nft.impl.domain.utils

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.web3j.protocol.Web3j
import org.web3j.protocol.Web3jService
import org.web3j.protocol.core.Response
import java.math.BigInteger

class Web3ResponseMappingTest {
    @Test
    fun successfulResponseIsTransformed() {
        val response = Response<String>().apply { result = "payload" }
        assertEquals(7, response.map { it.length })
    }

    @Test
    fun rpcErrorPreventsTransformAndPreservesDiagnostic() {
        val response = Response<String>().apply { error = Response.Error(-32000, "denied") }
        val failure = assertThrows(IllegalStateException::class.java) { response.map { "unused" } }
        assertEquals("Could not fetch web3 response due to \"denied\"", failure.message)
    }

    @Test
    fun malformedResultGetsTransformDiagnostic() {
        val response = Response<String>().apply { result = "bad" }
        val failure = assertThrows(IllegalStateException::class.java) { response.map<String, Int> { error("malformed") } }
        assertEquals("Could not transform web3 response due to malformed", failure.message)
    }

    @Test
    fun connectionAccessorsReturnServicesAndRejectMissingConnections() {
        val web3 = mockk<Web3j>()
        val service = mockk<Web3jService>()
        val connected = mockk<EthereumChainConnection> {
            every { web3j } returns web3
            every { this@mockk.service } returns service
        }
        assertSame(web3, connected.nonNullWeb3j)
        assertSame(service, connected.nonNullWeb3jService)
        val missing = mockk<EthereumChainConnection> {
            every { web3j } returns null
            every { this@mockk.service } returns null
        }
        assertEquals(
            "Established connection to web3 contains errors.",
            assertThrows(IllegalStateException::class.java) { missing.nonNullWeb3j }.message
        )
        assertEquals(
            "Could not have establish subscription to web3jService.",
            assertThrows(IllegalStateException::class.java) { missing.nonNullWeb3jService }.message
        )
    }

    @Test
    fun priorityFeeResponseDecodesHexQuantity() {
        val response = MaxPriorityFeePerGas().apply { result = "0x2a" }
        assertEquals(BigInteger.valueOf(42), response.maxPriorityFeePerGas)
    }
}

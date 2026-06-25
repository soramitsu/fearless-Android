package jp.co.soramitsu.core.rpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class RpcCallsTest {

    @Test
    fun `normalizes exact decimal payment fee strings`() {
        val fee = normalizePaymentPartialFee(
            mapOf("partialFee" to "123456789012345678901234567890")
        )

        assertEquals(BigInteger("123456789012345678901234567890"), fee)
    }

    @Test
    fun `normalizes hex integer rpc fields`() {
        val value = normalizeRpcBigInteger("0x0100", "nonce")

        assertEquals(BigInteger("256"), value)
    }

    @Test
    fun `rejects missing payment fee`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizePaymentPartialFee(mapOf("weight" to 100))
        }
    }

    @Test
    fun `rejects negative rpc integers`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRpcBigInteger("-1", "nonce")
        }
    }

    @Test
    fun `rejects fractional rpc integers`() {
        assertThrows(ArithmeticException::class.java) {
            normalizeRpcBigInteger(1.25, "nonce")
        }
    }

    @Test
    fun `extracts finalized block hash before in block hash`() {
        val finalizedHash = "0x" + "11".repeat(32)
        val inBlockHash = "0x" + "22".repeat(32)

        val blockHash = normalizeAuthorStatusBlockHash(
            mapOf(
                RpcCalls.IN_BLOCK to inBlockHash,
                RpcCalls.FINALIZED to finalizedHash
            )
        )

        assertEquals(finalizedHash, blockHash)
    }

    @Test
    fun `extracts in block hash when finalized hash is absent`() {
        val inBlockHash = "0x" + "22".repeat(32)

        val blockHash = normalizeAuthorStatusBlockHash(mapOf(RpcCalls.IN_BLOCK to inBlockHash))

        assertEquals(inBlockHash, blockHash)
    }

    @Test
    fun `ignores non terminal author statuses`() {
        assertNull(normalizeAuthorStatusBlockHash(mapOf("ready" to null)))
    }

    @Test
    fun `rejects malformed author block hash`() {
        assertNull(normalizeAuthorStatusBlockHash(mapOf(RpcCalls.FINALIZED to "0x1234")))
    }

    @Test
    fun `normalizes 32 byte rpc block hashes`() {
        val blockHash = "0x" + "33".repeat(32)

        assertEquals(blockHash, normalizeRpcBlockHash(blockHash, "block hash"))
    }

    @Test
    fun `rejects null rpc block hashes`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRpcBlockHash(null, "block hash")
        }
    }

    @Test
    fun `rejects malformed rpc block hashes`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRpcBlockHash("0x1234", "block hash")
        }
    }
}

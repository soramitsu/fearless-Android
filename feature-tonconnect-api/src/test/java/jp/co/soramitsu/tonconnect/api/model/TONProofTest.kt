package jp.co.soramitsu.tonconnect.api.model

import jp.co.soramitsu.fearless_utils.encrypt.ton.TonEd25519
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.AddrStd
import org.ton.crypto.digest.sha256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class TONProofTest {

    private val address = AddrStd("0:" + (0 until 32).joinToString("") { "%02x".format(it * 5) })

    @Test
    fun `proof signature is the one ton-kotlin made for the same request`() {
        for (index in 0 until 16) {
            val privateKey = ByteArray(32) { (it * 7 + index).toByte() }
            val payload = "payload-$index"
            val domain = if (index % 2 == 0) "example.com" else "dapp.ton.example"
            val timestamp = 1_700_000_000L + index

            val result = TONProof.sign(address, privateKey, payload, domain, timestamp)

            // 4.3.1: ton-kotlin SHA-256 over the same message, signed with PrivateKeyEd25519.
            val request = TONProof.Request(timestamp, payload, TONProof.Domain(domain), TONProof.Address(address))
            val body = sha256(byteArrayOf(-1, -1) + "ton-connect".encodeToByteArray() + sha256(request.message))
            val legacySignature = PrivateKeyEd25519(privateKey).sign(body)

            assertEquals(Base64.encode(legacySignature), result.signature)
            assertEquals(timestamp, result.timestamp)
            assertEquals(payload, result.payload)
            assertEquals(domain, result.domain.value)
            assertTrue(TonEd25519.verify(TonEd25519.publicKey(privateKey), body, Base64.decode(result.signature)))
        }
    }

    @Test
    fun `proof defaults to the current time in seconds`() {
        val before = System.currentTimeMillis() / 1000L
        val result = TONProof.sign(address, ByteArray(32) { 1 }, "p", "example.com")
        val after = System.currentTimeMillis() / 1000L
        assertTrue(result.timestamp in before..after)
    }
}

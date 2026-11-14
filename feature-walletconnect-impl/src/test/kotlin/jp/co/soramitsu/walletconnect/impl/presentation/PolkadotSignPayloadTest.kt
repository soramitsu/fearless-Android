package jp.co.soramitsu.walletconnect.impl.presentation

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger

class PolkadotSignPayloadTest {

    @Test
    fun `a polkadot js payload is read with every field as its type`() {
        val payload = PolkadotSignPayload.parse(json { }.toString())

        assertEquals("15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5", payload.address)
        assertArrayEquals(ByteArray(32) { 0xaa.toByte() }, payload.genesisHash)
        assertArrayEquals(ByteArray(32) { 0xbb.toByte() }, payload.blockHash)
        assertArrayEquals(byteArrayOf(0x05, 0x03), payload.method)
        assertArrayEquals(byteArrayOf(0xb5.toByte(), 0x03), payload.era)
        assertEquals(BigInteger.valueOf(34), payload.nonce)
        assertEquals(BigInteger.valueOf(3), payload.tip)
        assertEquals(2_005_000L, payload.specVersion)
        assertEquals(26L, payload.transactionVersion)
        assertEquals(4, payload.format.version)
        assertEquals(listOf("CheckSpecVersion", "CheckMetadataHash"), payload.format.signedExtensions)
        assertEquals(0, payload.format.metadataHashMode)
        assertNull(payload.format.metadataHash)
        assertFalse(payload.format.namesFeeAsset)
        assertFalse(payload.isImmortal)
    }

    @Test
    fun `optional format fields may be absent`() {
        val payload = PolkadotSignPayload.parse(
            json {
                listOf("address", "version", "signedExtensions", "mode", "metadataHash", "assetId", "blockNumber").forEach(::remove)
            }.toString()
        )

        assertNull(payload.address)
        assertNull(payload.format.version)
        assertNull(payload.format.signedExtensions)
        assertNull(payload.format.metadataHashMode)
    }

    @Test
    fun `hashes must be exactly 32 bytes so no field can carry the next one`() {
        listOf("genesisHash", "blockHash").forEach { key ->
            assertMalformed(json { put(key, JsonPrimitive("0x" + "aa".repeat(31))) })
            assertMalformed(json { put(key, JsonPrimitive("0x" + "aa".repeat(33))) })
            assertMalformed(json { put(key, JsonPrimitive("0x" + "aa".repeat(32) + "00")) })
        }
    }

    @Test
    fun `an era is one valid era encoding`() {
        // An era followed by a compact nonce, as the payload alignment attack crafted it.
        assertMalformed(json { put("era", JsonPrimitive("0xb5031c")) })
        assertMalformed(json { put("era", JsonPrimitive("0x")) })
        assertMalformed(json { put("era", JsonPrimitive("0x01")) })
        // Period 2 (0x0000 | 0) is below the minimum of 4; the phase must stay below the period.
        assertMalformed(json { put("era", JsonPrimitive("0x0001")) })
        assertMalformed(json { put("era", JsonPrimitive("0x05ff")) })
        assertTrue(PolkadotSignPayload.parse(json { put("era", JsonPrimitive("0x00")) }.toString()).isImmortal)
        assertTrue(PolkadotSignPayload.isValidEra(byteArrayOf(0xf5.toByte(), 0x00)))
        assertFalse(PolkadotSignPayload.isValidEra(byteArrayOf(0x00, 0x00)))
    }

    @Test
    fun `numbers must fit their types`() {
        assertMalformed(json { put("nonce", JsonPrimitive("0x" + "00".repeat(9))) })
        assertMalformed(json { put("tip", JsonPrimitive("0x" + "00".repeat(17))) })
        assertMalformed(json { put("specVersion", JsonPrimitive("0x0000000001")) })
        assertMalformed(json { put("transactionVersion", JsonPrimitive("0x0000000001")) })
        assertMalformed(json { put("nonce", JsonPrimitive(34)) })
        assertMalformed(json { put("mode", JsonPrimitive(2)) })
        assertMalformed(json { put("mode", JsonPrimitive("0")) })
        assertMalformed(json { put("version", JsonPrimitive("4")) })
    }

    @Test
    fun `hex must be prefixed and even`() {
        assertMalformed(json { put("method", JsonPrimitive("0503")) })
        assertMalformed(json { put("method", JsonPrimitive("0x050")) })
        assertMalformed(json { put("method", JsonPrimitive("0x05zz")) })
        assertMalformed(json { put("method", JsonPrimitive("0x05")) })
        assertMalformed(json { put("method", JsonPrimitive("0x" + "00".repeat(128 * 1024 + 1))) })
        assertMalformed(json { remove("method") })
        assertMalformed(json { put("metadataHash", JsonPrimitive("0x" + "11".repeat(31))) })
    }

    @Test
    fun `malformed signed extensions and payloads that are not objects are refused`() {
        assertMalformed(json { put("signedExtensions", JsonPrimitive("CheckNonce")) })
        assertMalformed(json { put("signedExtensions", buildJsonArray { add("") }) })
        assertMalformed(json { put("address", JsonPrimitive(1)) })
        listOf("not json", "[]", "\"0x00\"").forEach { message ->
            try {
                PolkadotSignPayload.parse(message)
                fail("$message must be refused")
            } catch (expected: WalletConnectRequestRejectedException) {
                // refused
            }
        }
    }

    @Test
    fun `a fee asset or an application id is recorded for the format check`() {
        assertTrue(PolkadotSignPayload.parse(json { put("assetId", JsonPrimitive("0x0100")) }.toString()).format.namesFeeAsset)
        val withAppId = json { put("appId", JsonPrimitive(7)) }
        assertEquals(BigInteger.valueOf(7), PolkadotSignPayload.parse(withAppId.toString()).format.appId)
        assertMalformed(json { put("appId", JsonPrimitive("0x0100000000")) })
    }

    private fun json(change: MutableMap<String, JsonElement>.() -> Unit): JsonObject {
        val fields = mutableMapOf<String, JsonElement>(
            "address" to JsonPrimitive("15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5"),
            "assetId" to JsonNull,
            "blockHash" to JsonPrimitive("0x" + "bb".repeat(32)),
            "blockNumber" to JsonPrimitive("0x01234567"),
            "era" to JsonPrimitive("0xb503"),
            "genesisHash" to JsonPrimitive("0x" + "aa".repeat(32)),
            "metadataHash" to JsonNull,
            "method" to JsonPrimitive("0x0503"),
            "mode" to JsonPrimitive(0),
            "nonce" to JsonPrimitive("0x00000022"),
            "signedExtensions" to buildJsonArray {
                add("CheckSpecVersion")
                add("CheckMetadataHash")
            },
            "specVersion" to JsonPrimitive("0x001e9808"),
            "tip" to JsonPrimitive("0x00000000000000000000000000000003"),
            "transactionVersion" to JsonPrimitive("0x0000001a"),
            "version" to JsonPrimitive(4),
            "withSignedTransaction" to JsonPrimitive(true)
        )
        fields.change()
        return JsonObject(fields)
    }

    private fun assertMalformed(payload: JsonObject) {
        try {
            PolkadotSignPayload.parse(payload.toString())
            fail("$payload must be refused")
        } catch (expected: WalletConnectRequestRejectedException) {
            // refused before anything is decoded or signed
        }
    }
}

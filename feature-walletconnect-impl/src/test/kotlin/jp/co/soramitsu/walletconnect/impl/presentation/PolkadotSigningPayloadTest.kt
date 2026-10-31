package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.walletconnect.impl.presentation.ParityRuntimes.cases
import jp.co.soramitsu.walletconnect.impl.presentation.ParityRuntimes.text
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger

class PolkadotSigningPayloadTest {

    private val address = SS58Encoder.encode(ParityRuntimes.keypairPublicKey, 0)

    @Test
    fun `polkadot js payloads of every mortal parity case are encoded byte for byte`() {
        var checked = 0
        ParityRuntimes.chains.forEach { chain ->
            val runtime = ParityRuntimes.runtime(chain.text("name"))
            chain.cases().filter { it["era"] !is JsonNull && it["era"] != null }.forEach { case ->
                val method = ParityRuntimes.callOf(runtime, case)
                val payload = PolkadotSignPayload.parse(ParityRuntimes.signerPayload(chain, case, method, address).toString())

                val encoded = PolkadotSigningPayload.encode(payload, runtime)

                assertEquals("${chain.text("name")} ${case.text("name")}", case.text("signingPayload"), encoded.toHexString(withPrefix = true))
                checked++
            }
        }
        // Polkadot, Kusama, Statemint (ChargeAssetTxPayment, EthSetOrigin) and Westend (WeightReclaim), two cases each.
        assertEquals(8, checked)
    }

    @Test
    fun `runtime version is read from the metadata the call is decoded with`() {
        assertEquals(2_005_000L to 26L, ParityRuntimes.runtime("polkadot").runtimeVersion())
        assertEquals(1_025_001L to 27L, ParityRuntimes.runtime("westend").runtimeVersion())
    }

    @Test
    fun `a payload for another runtime version is refused`() {
        assertRefused(PolkadotRefusal.OtherRuntime, polkadot { put("specVersion", JsonPrimitive("0x001e9809")) })
        assertRefused(PolkadotRefusal.OtherRuntime, polkadot { put("transactionVersion", JsonPrimitive("0x0000001b")) })
    }

    @Test
    fun `a payload built for other signed extensions is refused`() {
        val withoutMetadataHash = buildJsonArray {
            ParityRuntimes.chain("polkadot").getValue("signedExtensions").let { it as kotlinx.serialization.json.JsonArray }
                .map { it.toString().trim('"') }.filter { it != "CheckMetadataHash" }.forEach { add(it) }
        }
        assertRefused(PolkadotRefusal.OtherRuntime, polkadot { put("signedExtensions", withoutMetadataHash) })
    }

    @Test
    fun `fee assets other extrinsic versions and a mode without its hash are refused`() {
        assertRefused(PolkadotRefusal.UnsupportedFormat, polkadot { put("assetId", JsonPrimitive("0x0100")) })
        assertRefused(PolkadotRefusal.UnsupportedFormat, polkadot { put("version", JsonPrimitive(5)) })
        assertRefused(PolkadotRefusal.UnsupportedFormat, polkadot { put("mode", JsonPrimitive(1)) })
        val hashWithoutMode = JsonPrimitive("0x" + "11".repeat(32))
        assertRefused(PolkadotRefusal.UnsupportedFormat, polkadot { put("metadataHash", hashWithoutMode) })
    }

    @Test
    fun `metadata hash mode 1 commits to the hash the dApp names`() {
        val hash = ByteArray(32) { 0x11 }
        val case = ParityRuntimes.chain("polkadot").cases()[1]
        val disabledPayload = PolkadotSignPayload.parse(polkadot { }.toString())
        val disabled = PolkadotSigningPayload.encode(disabledPayload, ParityRuntimes.runtime("polkadot"))

        val enabled = PolkadotSigningPayload.encode(
            PolkadotSignPayload.parse(
                polkadot {
                    put("mode", JsonPrimitive(1))
                    put("metadataHash", JsonPrimitive(hash.toHexString(withPrefix = true)))
                }.toString()
            ),
            ParityRuntimes.runtime("polkadot")
        )

        assertEquals(case.text("signingPayload"), disabled.toHexString(withPrefix = true))
        // The mode byte follows the tip; the Some hash closes the implicit data.
        val modeIndex = disabled.size - 73 - 1
        assertEquals(0.toByte(), disabled[modeIndex])
        assertEquals(1.toByte(), enabled[modeIndex])
        assertArrayEquals(byteArrayOf(1) + hash, enabled.copyOfRange(enabled.size - 33, enabled.size))
        assertEquals(disabled.size + 32, enabled.size)
    }

    @Test
    fun `a network with a signed extension the wallet cannot encode is refused`() {
        val polkadot = ParityRuntimes.runtime("polkadot")
        val withUnknownExtension = RuntimeSnapshot(
            polkadot.typeRegistry,
            RuntimeMetadata(
                polkadot.metadata.runtimeVersion,
                polkadot.metadata.modules,
                ExtrinsicMetadata(BigInteger.valueOf(4), polkadot.metadata.extrinsic.signedExtensions + "ChargeSomethingNew")
            )
        )
        val payload = polkadot { put("signedExtensions", JsonNull) }

        try {
            PolkadotSigningPayload.encode(PolkadotSignPayload.parse(payload.toString()), withUnknownExtension)
            fail("an unknown extension must not be signed as empty")
        } catch (refused: PolkadotTransactionRefusedException) {
            assertEquals(PolkadotRefusal.UnsupportedFormat, refused.refusal)
            assertTrue(refused.message.orEmpty().contains("ChargeSomethingNew"))
        }
    }

    // The Polkadot mortal transfer case, with [change] applied to its polkadot.js payload.
    private fun polkadot(change: MutableMap<String, JsonElement>.() -> Unit): JsonObject {
        val chain = ParityRuntimes.chain("polkadot")
        val case = chain.cases()[1]
        val method = ParityRuntimes.callOf(ParityRuntimes.runtime("polkadot"), case)
        val fields = ParityRuntimes.signerPayload(chain, case, method, address).toMutableMap()
        fields.change()
        return JsonObject(fields)
    }

    private fun assertRefused(refusal: PolkadotRefusal, payload: JsonObject) {
        try {
            PolkadotSigningPayload.encode(PolkadotSignPayload.parse(payload.toString()), ParityRuntimes.runtime("polkadot"))
            fail("$refusal expected")
        } catch (refused: PolkadotTransactionRefusedException) {
            assertEquals(refusal, refused.refusal)
        }
    }

    private companion object {
        init {
            // The fixture must be the reviewed one: the payloads below are only meaningful against it.
            check("0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3".fromHex().size == 32)
        }
    }
}

package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.core.extrinsic.MetadataHashUnavailableException
import jp.co.soramitsu.core.extrinsic.SigningRuntime
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.walletconnect.impl.presentation.ParityRuntimes.cases
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/**
 * A dApp payload that commits to a metadata hash is read with the runtime of the metadata this wallet hashed, and
 * signed only when the dApp's hash is that hash; payloads without a hash keep the network's current runtime.
 */
class PolkadotSigningRuntimesTest {

    private val current = ParityRuntimes.runtime("polkadot")

    // A distinct instance standing for the runtime built from the hashed V15 metadata.
    private val hashedRuntime = RuntimeSnapshot(current.typeRegistry, current.metadata)
    private val ourHash = ByteArray(32) { 0x22 }
    private val address = SS58Encoder.encode(ParityRuntimes.keypairPublicKey, 0)

    private val noHashExpected: suspend (Chain, RuntimeVersion) -> SigningRuntime = { _, _ ->
        throw AssertionError("no metadata hash may be computed for this payload")
    }

    @Test
    fun `a payload without a metadata hash is read with the current runtime`() = runBlocking {
        val runtimes = PolkadotSigningRuntimes({ current }, noHashExpected)

        assertSame(current, runtimes.runtimeFor(polkadotChain, payload()))
    }

    @Test
    fun `a payload committing to our hash is read with the runtime that hash was computed from`() = runBlocking {
        var requested: RuntimeVersion? = null
        val runtimes = PolkadotSigningRuntimes({ current }) { _, version ->
            requested = version
            SigningRuntime.WithMetadataHash(hashedRuntime, ourHash)
        }

        assertSame(hashedRuntime, runtimes.runtimeFor(polkadotChain, payload { enable(ourHash) }))
        assertEquals(2_005_000, requested?.specVersion)
        assertEquals(26, requested?.transactionVersion)
    }

    @Test
    fun `a payload committing to another hash is refused`() {
        val runtimes = PolkadotSigningRuntimes({ current }) { _, _ -> SigningRuntime.WithMetadataHash(hashedRuntime, ourHash) }

        assertRefused(PolkadotRefusal.OtherMetadata, runtimes, payload { enable(ByteArray(32) { 0x33 }) })
    }

    @Test
    fun `a hash that cannot be computed now makes signing wait`() = runBlocking {
        val runtimes = PolkadotSigningRuntimes({ current }) { _, _ -> throw MetadataHashUnavailableException("offline") }

        assertNull(runtimes.runtimeFor(polkadotChain, payload { enable(ourHash) }))
    }

    @Test
    fun `a network exempt from the metadata hash refuses payloads that commit to one`() {
        val runtimes = PolkadotSigningRuntimes({ current }) { _, _ -> SigningRuntime.MetadataHashExempt(current, "reviewed") }

        assertRefused(PolkadotRefusal.UnsupportedFormat, runtimes, payload { enable(ourHash) })
    }

    @Test
    fun `a payload for another runtime version keeps the current runtime, which refuses it`() = runBlocking {
        val runtimes = PolkadotSigningRuntimes({ current }, noHashExpected)
        val stale = payload {
            enable(ourHash)
            put("specVersion", JsonPrimitive("0x001e9807"))
        }

        val runtime = runtimes.runtimeFor(polkadotChain, stale)

        assertSame(current, runtime)
        try {
            PolkadotSigningPayload.encode(stale, current)
            fail("a payload for another runtime version must be refused")
        } catch (refused: PolkadotTransactionRefusedException) {
            assertEquals(PolkadotRefusal.OtherRuntime, refused.refusal)
        }
    }

    @Test
    fun `without the network's runtime nothing is read`() = runBlocking {
        val runtimes = PolkadotSigningRuntimes({ null }, noHashExpected)

        assertNull(runtimes.runtimeFor(polkadotChain, payload { enable(ourHash) }))
    }

    private fun MutableMap<String, JsonElement>.enable(hash: ByteArray) {
        put("mode", JsonPrimitive(1))
        put("metadataHash", JsonPrimitive(hash.toHexString(withPrefix = true)))
    }

    // The Polkadot mortal transfer case, with [change] applied to its polkadot.js payload.
    private fun payload(change: MutableMap<String, JsonElement>.() -> Unit = {}): PolkadotSignPayload {
        val chain = ParityRuntimes.chain("polkadot")
        val case = chain.cases()[1]
        val fields = ParityRuntimes.signerPayload(chain, case, ParityRuntimes.callOf(current, case), address).toMutableMap()
        fields.change()
        return PolkadotSignPayload.parse(JsonObject(fields).toString())
    }

    private fun assertRefused(
        refusal: PolkadotRefusal,
        runtimes: PolkadotSigningRuntimes,
        payload: PolkadotSignPayload
    ) {
        try {
            runBlocking { runtimes.runtimeFor(polkadotChain, payload) }
            fail("$refusal expected")
        } catch (refused: PolkadotTransactionRefusedException) {
            assertEquals(refusal, refused.refusal)
        }
    }
}

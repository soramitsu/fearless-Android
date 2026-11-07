package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import com.google.gson.Gson
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.getResourceReader
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.MetadataHashAdditional
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.SignedExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.zip.GZIPInputStream

/**
 * Transactions signed with `CheckMetadataHash` Mode::Enabled and the RFC 78 hash, built from V15 metadata, against an
 * independent encoder (py-scale-codec with the V14 metadata of the same block, see `extrinsic-vectors.json`).
 */
class MetadataHashExtrinsicTest {

    private class Vectors(val chains: List<Chain>)

    private class Chain(
        val chain: String,
        val metadata: String,
        val specVersion: Int,
        val transactionVersion: Int,
        val genesisHash: String,
        val blockHash: String,
        val metadataHash: String,
        val cases: List<Case>
    )

    private class Case(
        val name: String,
        val seed: String,
        val dest: String,
        val value: String,
        val nonce: Int,
        val tip: String,
        val period: Int?,
        val current: Long?,
        val enabled: Boolean,
        val publicKey: String,
        val signingPayload: String,
        val extrinsic: String
    )

    private val vectors = Gson().fromJson(getResourceReader("metadata-hash/extrinsic-vectors.json"), Vectors::class.java)

    private val types = mapOf(
        "westend" to "parity/westend_parity_types.json",
        "polkadot" to "parity/polkadot_parity_types.json",
        "polkadot-assethub" to "parity/statemint_parity_types.json"
    )

    private fun runtime(chain: Chain): RuntimeSnapshot {
        val stream = requireNotNull(javaClass.classLoader!!.getResourceAsStream(chain.metadata))
        val hex = GZIPInputStream(stream).use { it.readBytes() }.toHexString(withPrefix = true)
        return ParityFixtures.buildRuntime(hex, types.getValue(chain.chain))
    }

    private fun builder(runtime: RuntimeSnapshot, chain: Chain, case: Case, metadataHash: ByteArray?): ExtrinsicBuilder {
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, case.seed.fromHex())
        assertEquals(case.publicKey, keypair.publicKey.toHexString())
        val mortal = case.period != null
        return ExtrinsicBuilder(
            runtime = runtime,
            keypair = keypair,
            nonce = case.nonce.toBigInteger(),
            runtimeVersion = RuntimeVersion(chain.specVersion, chain.transactionVersion),
            genesisHash = chain.genesisHash.fromHex(),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = multiAddressFromId(keypair.publicKey),
            blockHash = (if (mortal) chain.blockHash else chain.genesisHash).fromHex(),
            era = if (mortal) Era.Mortal(case.period!!, (case.current!! % case.period).toInt()) else Era.Immortal,
            tip = BigInteger(case.tip),
            metadataHash = metadataHash
        ).call("Balances", "transfer_keep_alive", mapOf("dest" to multiAddressFromId(case.dest.fromHex()), "value" to BigInteger(case.value)))
    }

    @Test
    fun `signed payloads and extrinsics match the independent encoder`() {
        vectors.chains.forEach { chain ->
            val runtime = runtime(chain)
            chain.cases.forEach { case ->
                val label = "${chain.chain} ${case.name}"
                val hash = chain.metadataHash.fromHex().takeIf { case.enabled }
                val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, case.seed.fromHex())
                var signed: ByteArray? = null
                val prepared = builder(runtime, chain, case, hash).prepareSigning().sign(keypair) { encryption, message, key ->
                    signed = message
                    Signer.sign(encryption, message, key)
                }
                assertEquals(label, case.signingPayload, signed!!.toHexString(withPrefix = true))
                assertEquals(label, case.extrinsic, prepared)
                assertEquals(label, case.extrinsic, builder(runtime, chain, case, hash).build())
            }
        }
    }

    @Test
    fun `the extrinsic carries Mode Enabled and the payload ends with Some(hash)`() {
        val chain = vectors.chains.single { it.chain == "westend" }
        val runtime = runtime(chain)
        val case = chain.cases.single { it.name == "enabled-mortal" }
        val extrinsic = Extrinsic.fromHex(runtime, builder(runtime, chain, case, chain.metadataHash.fromHex()).build())
        assertEquals(BigInteger.ONE, extrinsic.signature!!.signedExtras[SignedExtras.CHECK_METADATA_HASH])
        assertTrue(case.signingPayload.endsWith("01" + chain.metadataHash.removePrefix("0x")))
    }

    @Test
    fun `the additional data type encodes None for Disabled and Some for a hash`() {
        val runtime = runtime(vectors.chains.single { it.chain == "westend" })
        val hash = ByteArray(32) { 3 }
        assertEquals("0x00", MetadataHashAdditional.toHex(runtime, null))
        assertEquals("0x00", MetadataHashAdditional.toHex(runtime, BigInteger.ZERO))
        assertEquals("0x01" + hash.toHexString(), MetadataHashAdditional.toHex(runtime, hash))
        assertArrayEquals(hash, MetadataHashAdditional.fromHex(runtime, "0x01" + hash.toHexString()) as ByteArray)
        assertEquals(BigInteger.ZERO, MetadataHashAdditional.fromHex(runtime, "0x00"))
    }

    @Test
    fun `a metadata hash must be 32 bytes`() {
        val chain = vectors.chains.single { it.chain == "westend" }
        val case = chain.cases.first()
        assertThrows(IllegalArgumentException::class.java) { builder(runtime(chain), chain, case, ByteArray(31)) }
    }

    @Test
    fun `a call resolved against other runtime metadata is refused`() {
        val chain = vectors.chains.single { it.chain == "westend" }
        val own = runtime(chain)
        val other = runtime(chain)
        val balances = other.metadata.module("Balances")
        val foreign = GenericCall.Instance(
            balances,
            balances.call("transfer_keep_alive"),
            mapOf("dest" to multiAddressFromId(ByteArray(32)), "value" to BigInteger.ONE)
        )
        val builder = builder(own, chain, chain.cases.first(), chain.metadataHash.fromHex())
        assertThrows(IllegalArgumentException::class.java) {
            builder.call("Utility", "batch_all", mapOf("calls" to listOf(foreign)))
        }
    }
}

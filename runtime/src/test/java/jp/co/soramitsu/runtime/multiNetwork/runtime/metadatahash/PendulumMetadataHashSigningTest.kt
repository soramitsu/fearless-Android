package jp.co.soramitsu.runtime.multiNetwork.runtime.metadatahash

import com.google.gson.JsonParser
import jp.co.soramitsu.core.extrinsic.SigningRuntime
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.network.JsonFactory
import jp.co.soramitsu.core.runtime.RuntimeFactory
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.SignedExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import jp.co.soramitsu.runtime.assethub.AssetHubRuntimeTypes
import jp.co.soramitsu.runtime.multiNetwork.chain.model.pendulumChainId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import java.math.BigInteger
import java.util.zip.GZIPInputStream

/**
 * Pendulum signs with Mode::Enabled since spec pendulum 27 was built with a metadata hash (live check, 13 November 2026),
 * so [MetadataHashExemptions.Default] no longer lists it. Its signing runtime is built as `RuntimeModule` builds it: the
 * V15 metadata with Pendulum's entry of the production types catalogue. The fixture is spec 26's V15 metadata, with the
 * hash the merkleized-metadata crate computed for it (runtime-utils `metadata-hash/vectors.json`).
 */
class PendulumMetadataHashSigningTest {

    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "settings.gradle").isFile }
    private val fixtures = File(root, "runtime-utils/src/test/resources/metadata-hash")
    private val vector = JsonParser.parseString(File(fixtures, "vectors.json").readText()).asJsonObject["chains"].asJsonArray
        .map { it.asJsonObject }.single { it["chainId"].asString == pendulumChainId }
    private val metadata = GZIPInputStream(File(fixtures, "pendulum-v15.scale.gz").inputStream()).use { it.readBytes() }

    // What RuntimeSyncService stores as the network's types: its element of the production types catalogue.
    private val types = JsonParser.parseString(File(root, "remote-config/production/chains/all_chains_types_android.json").readText())
        .asJsonArray.map { it.asJsonObject }.single { it["chainId"].asString == pendulumChainId }.toString()
    private val version = RuntimeVersion(vector["specVersion"].asInt, vector["transactionVersion"].asInt)
    private val chain = mock<IChain> { on { id } doReturn pendulumChainId }

    private val remote = object : SigningMetadataRemote {
        override suspend fun metadataV15(chainId: String): ByteArray = metadata
        override suspend fun systemProperties(chainId: String): Map<*, *> = mapOf(
            "tokenDecimals" to listOf(vector["decimals"].asDouble),
            "tokenSymbol" to listOf(vector["tokenSymbol"].asString)
        )
    }

    private fun build(chainId: String, metadataV15: ByteArray, specVersion: Int): RuntimeSnapshot =
        RuntimeFactory(JsonFactory.create()).constructRuntime(
            metadataRaw = metadataV15.toHexString(withPrefix = true),
            ownTypesRaw = requireNotNull(AssetHubRuntimeTypes.forChain(chainId, types)),
            runtimeVersion = specVersion
        ).runtime

    @Test
    fun `Pendulum signs a transfer with Mode Enabled and the hash of its V15 metadata`() = runBlocking {
        val current = build(pendulumChainId, metadata, version.specVersion)
        val source = MetadataHashSigningRuntimeSource({ current }, remote, { chainId, bytes, specVersion -> build(chainId, bytes, specVersion) })

        val signing = source.signingRuntime(chain, version)

        signing as SigningRuntime.WithMetadataHash
        val hash = vector["metadataHash"].asString
        assertEquals(hash, signing.metadataHash.toHexString(withPrefix = true))

        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 7 })
        var signed: ByteArray? = null
        val extrinsic = ExtrinsicBuilder(
            runtime = signing.runtime,
            keypair = keypair,
            nonce = BigInteger.ZERO,
            runtimeVersion = version,
            genesisHash = pendulumChainId.fromHex(),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = multiAddressFromId(keypair.publicKey),
            blockHash = vector["block"].asString.fromHex(),
            era = Era.Mortal(64, 0),
            metadataHash = signing.metadataHashToSign()
        ).call("Balances", "transfer_keep_alive", mapOf("dest" to multiAddressFromId(ByteArray(32) { 1 }), "value" to BigInteger.TEN.pow(10)))
            .prepareSigning()
            .sign(keypair) { encryption, message, key ->
                signed = message
                Signer.sign(encryption, message, key)
            }

        // The extrinsic carries Mode::Enabled, and the signed payload ends with Some(hash): CheckMetadataHash is last.
        assertEquals(BigInteger.ONE, Extrinsic.fromHex(signing.runtime, extrinsic).signature!!.signedExtras[SignedExtras.CHECK_METADATA_HASH])
        assertTrue(signed!!.toHexString().endsWith("01" + hash.removePrefix("0x")))
    }
}

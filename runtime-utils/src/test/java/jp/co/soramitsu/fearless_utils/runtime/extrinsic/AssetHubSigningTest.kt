package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import com.google.gson.Gson
import com.google.gson.JsonObject
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.AdditionalExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.SignedExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Null
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.fearless_utils.runtime.metadata.SignedExtensionTypes
import jp.co.soramitsu.fearless_utils.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.math.BigInteger
import java.util.zip.GZIPInputStream

@RunWith(Parameterized::class)
class AssetHubSigningTest(private val network: String) {
    private val runtime = runtimes.getValue(network)
    private val vectors = javaClass.getResourceAsStream("/asset-hub/$network-signing-vectors.json")!!.reader().use {
        Gson().fromJson(it, JsonObject::class.java)
    }
    private fun vector(name: String) = vectors[name].asString
    private val extras = mapOf(
        SignedExtras.ERA to Era.Immortal,
        SignedExtras.NONCE to 34.toBigInteger(),
        SignedExtras.TIP to BigInteger.ZERO,
        SignedExtras.ASSET_TX_PAYMENT to listOf(BigInteger.ZERO, null)
    )
    private val additional = mapOf(
        AdditionalExtras.BLOCK_HASH to vector("blockHash").fromHex(),
        AdditionalExtras.GENESIS to vector("genesis").fromHex(),
        AdditionalExtras.SPEC_VERSION to vectors["specVersion"].asInt.toBigInteger(),
        AdditionalExtras.TX_VERSION to vectors["transactionVersion"].asInt.toBigInteger()
    )

    @Test fun `actual metadata preserves typed extra and additional contracts`() {
        assertEquals(runtime.metadata.extrinsic.signedExtensions.toSet(), runtime.metadata.extrinsic.signedExtensionTypes.keys)
        assertEquals(vector("signedExtras"), SignedExtras.toHex(runtime, extras))
        assertEquals(vector("additionalExtras"), AdditionalExtras.toHex(runtime, additional))
    }

    @Test fun `complete payload and signed envelope match independent SCALE vectors without key access`() {
        val unreadable = object : Keypair {
            override val privateKey: ByteArray get() = error("private key accessed")
            override val publicKey: ByteArray get() = error("public key accessed")
        }
        for (mortal in listOf(false, true)) {
        val detail = if (mortal) vectors["mortal"].asJsonObject else vectors
        val builder = ExtrinsicBuilder(
            runtime, unreadable, 34.toBigInteger(),
            RuntimeVersion(vectors["specVersion"].asInt, vectors["transactionVersion"].asInt),
            vector("genesis").fromHex(), MultiChainEncryption.Substrate(EncryptionType.ED25519),
            multiAddressFromId(vector("publicKey").fromHex()), blockHash = detail["blockHash"].asString.fromHex(),
            era = if (mortal) Era.Mortal(64, 59) else Era.Immortal
        ).call("Staking", "bond", mapOf("value" to 12345.toBigInteger(), "payee" to DictEnum.Entry("Staked", null)))
        var signedPayload: String? = null
        val encoded = builder.prepareSigning().sign(unreadable) { _, payload, _ ->
            signedPayload = payload.toHexString(withPrefix = true)
            SignatureWrapper.Ed25519(ByteArray(64))
        }
        assertEquals(detail["payload"].asString, signedPayload)
        assertEquals(detail["zeroSignatureExtrinsic"].asString, encoded)
        }
    }

    @Test fun `unknown nonempty extra and additional contracts cannot be silently omitted`() {
        assertTrue(runCatching { SignedExtras.toHex(withExtra("Future", SignedExtensionTypes(u32, Null)), extras) }.isFailure)
        assertTrue(runCatching { AdditionalExtras.toHex(withExtra("Future", SignedExtensionTypes(Null, u32)), additional) }.isFailure)
    }

    @Test fun `unknown zero sized contracts are proven safe using portable metadata`() {
        assertEquals(vector("signedExtras"), SignedExtras.toHex(withExtra("Future", SignedExtensionTypes(Null, Null)), extras))
        assertEquals(vector("additionalExtras"), AdditionalExtras.toHex(withExtra("Future", SignedExtensionTypes(Null, Null)), additional))
    }

    @Test fun `changed known extension encoding is rejected`() {
        val types = runtime.metadata.extrinsic.signedExtensionTypes + ("CheckNonce" to SignedExtensionTypes(u32, Null))
        assertTrue(runCatching { SignedExtras.toHex(replaceTypes(types), extras) }.isFailure)
    }

    @Test fun `duplicate extension names are rejected`() {
        val original = runtime.metadata.extrinsic
        val changed = RuntimeSnapshot(runtime.typeRegistry, RuntimeMetadata(runtime.metadata.runtimeVersion, runtime.metadata.modules,
            ExtrinsicMetadata(original.version, original.signedExtensions + "CheckNonce", original.signedExtensionTypes)))
        assertTrue(runCatching { SignedExtras.toHex(changed, extras) }.isFailure)
    }

    private fun withExtra(name: String, type: SignedExtensionTypes): RuntimeSnapshot {
        val original = runtime.metadata.extrinsic
        return RuntimeSnapshot(runtime.typeRegistry, RuntimeMetadata(runtime.metadata.runtimeVersion, runtime.metadata.modules,
            ExtrinsicMetadata(original.version, original.signedExtensions + name, original.signedExtensionTypes + (name to type))))
    }

    private fun replaceTypes(types: Map<String, SignedExtensionTypes>): RuntimeSnapshot {
        val original = runtime.metadata.extrinsic
        return RuntimeSnapshot(runtime.typeRegistry, RuntimeMetadata(runtime.metadata.runtimeVersion, runtime.metadata.modules,
            ExtrinsicMetadata(original.version, original.signedExtensions, types)))
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun networks() = listOf("polkadot", "kusama", "westend")
        private val runtimes by lazy {
            networks().associateWith { network ->
                val data = GZIPInputStream(AssetHubSigningTest::class.java.getResourceAsStream("/asset-hub/$network-metadata.scale.gz")!!).use { it.readBytes() }
                val raw = RuntimeMetadataReader.read(data.toHexString(withPrefix = true))
                val parsed = TypesParserV14.parse(raw.metadata[RuntimeMetadataSchemaV14.lookup], v14Preset())
                val tree = AssetHubSigningTest::class.java.getResourceAsStream("/asset-hub/$network-types.json")!!.reader().use {
                    Gson().fromJson(it, TypeDefinitionsTree::class.java)
                }
                val preset = TypeDefinitionParser.parseNetworkVersioning(tree, parsed.typePreset).typePreset
                val registry = TypeRegistry(preset, DynamicTypeResolver.defaultCompoundResolver())
                RuntimeSnapshot(registry, VersionedRuntimeBuilder.buildMetadata(raw, registry))
            }
        }
    }
}

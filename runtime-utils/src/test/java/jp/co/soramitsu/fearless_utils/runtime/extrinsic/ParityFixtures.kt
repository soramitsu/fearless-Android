package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.getResourceReader
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.fearless_utils.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import java.math.BigInteger
import java.util.zip.GZIPInputStream

/**
 * Golden vectors in `resources/parity`: live V14 metadata of chains that list CheckMetadataHash, with extrinsics produced by
 * production shared_utils@7508bc83 (Play 4.2.0) and rebuilt byte-for-byte by @polkadot/types. See `provenance` in the JSON.
 */
object ParityFixtures {

    class Vectors(val keypair: KeypairJson, val chains: List<Chain>)

    class KeypairJson(val publicKey: String, val privateKey: String)

    class Chain(
        val name: String,
        val rpc: String,
        val specVersion: Int,
        val transactionVersion: Int,
        val genesisHash: String,
        val metadata: String,
        val metadataSha256: String,
        val types: String,
        val signedExtensions: List<String>,
        val cases: List<Case>
    )

    class Case(
        val name: String,
        val nonce: Int,
        val era: List<Int>?,
        val blockHash: String?,
        val tip: String,
        val batchAll: Boolean,
        val calls: List<TransferCall>,
        val signingPayload: String,
        val signedMessage: String,
        val extrinsic: String,
        val production: ProductionBytes?
    )

    class TransferCall(val module: String, val call: String, val dest: String, val value: String)

    class ProductionBytes(val extrinsic: String)

    val vectors: Vectors by lazy {
        Gson().fromJson(getResourceReader("parity/extrinsic-vectors.json"), Vectors::class.java)
    }

    val keypair: Keypair by lazy {
        BaseKeypair(
            privateKey = vectors.keypair.privateKey.fromHex(),
            publicKey = vectors.keypair.publicKey.fromHex()
        )
    }

    fun chain(name: String): Chain = vectors.chains.single { it.name == name }

    fun metadataHex(chain: Chain): String {
        val stream = requireNotNull(javaClass.classLoader!!.getResourceAsStream(chain.metadata))
        return GZIPInputStream(stream).bufferedReader().use { it.readText() }.trim()
    }

    fun runtime(chain: Chain): RuntimeSnapshot = buildRuntime(metadataHex(chain), chain.types)

    /** Mirrors the app's V14 path: lookup types from metadata plus per-network overrides. */
    fun buildRuntime(metadataHex: String, typesResource: String): RuntimeSnapshot {
        val raw = RuntimeMetadataReader.read(metadataHex)
        val parseResult = TypesParserV14.parse(
            lookup = raw.metadata[RuntimeMetadataSchemaV14.lookup],
            typePreset = v14Preset()
        )
        val tree = Gson().fromJson<TypeDefinitionsTree>(
            JsonReader(getResourceReader(typesResource)),
            TypeDefinitionsTree::class.java
        )
        val networkParsed = TypeDefinitionParser.parseNetworkVersioning(
            tree = tree,
            typePreset = parseResult.typePreset
        )
        val typeRegistry = TypeRegistry(networkParsed.typePreset, DynamicTypeResolver.defaultCompoundResolver())
        return RuntimeSnapshot(typeRegistry, VersionedRuntimeBuilder.buildMetadata(raw, typeRegistry))
    }

    fun builder(
        runtime: RuntimeSnapshot,
        chain: Chain,
        case: Case,
        keypair: Keypair = this.keypair,
        appId: BigInteger? = null
    ): ExtrinsicBuilder {
        val genesis = chain.genesisHash.fromHex()
        val builder = ExtrinsicBuilder(
            runtime = runtime,
            keypair = keypair,
            nonce = case.nonce.toBigInteger(),
            runtimeVersion = RuntimeVersion(chain.specVersion, chain.transactionVersion),
            genesisHash = genesis,
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = multiAddressFromId(keypair.publicKey),
            blockHash = case.blockHash?.fromHex() ?: genesis,
            era = case.era?.let { (period, phase) -> Era.Mortal(period, phase) } ?: Era.Immortal,
            tip = BigInteger(case.tip),
            appId = appId
        )
        case.calls.forEach { call ->
            builder.call(
                call.module,
                call.call,
                mapOf(
                    "dest" to multiAddressFromId(call.dest.fromHex()),
                    "value" to BigInteger(call.value)
                )
            )
        }
        return builder
    }
}

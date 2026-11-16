package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.metadata.callOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.hash.ChainMetadataHash
import jp.co.soramitsu.fearless_utils.runtime.metadata.hash.FrameMetadataV15
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.SecureRandom

/**
 * Read-only proof that live runtimes accept the RFC 78 hash this app computes (manual, never in CI by default):
 * `FEARLESS_UTILS_LIVE_VALIDATE=1 ./gradlew :runtime-utils:testDebugUnitTest --tests '*LiveMetadataHashValidateTest*'`.
 *
 * For each network the V15 metadata, `system_properties` and runtime version are read over RPC, the hash is computed
 * here, and a transfer built from that V15 metadata is signed with Mode::Enabled by a fresh, unfunded ED25519 key.
 * `TaggedTransactionQueue_validate_transaction` must answer `Invalid(Payment)`: the runtime decoded the extrinsic and
 * every extension and verified the signature over the payload with its own compiled-in hash. A wrong hash or encoding
 * gives `Invalid(BadProof)`. Nothing is ever submitted.
 */
class LiveMetadataHashValidateTest {

    private class LiveChain(val name: String, val rpc: String, val exempt: Boolean = false)

    private val chains = listOf(
        LiveChain("westend", "https://westend-rpc.polkadot.io"),
        LiveChain("polkadot", "https://rpc.polkadot.io"),
        LiveChain("kusama", "https://kusama-rpc.polkadot.io"),
        LiveChain("polkadot asset hub", "https://polkadot-asset-hub-rpc.polkadot.io"),
        LiveChain("kusama asset hub", "https://kusama-asset-hub-rpc.polkadot.io"),
        LiveChain("polkadot people", "https://polkadot-people-rpc.polkadot.io"),
        LiveChain("kusama people", "https://kusama-people-rpc.polkadot.io"),
        LiveChain("polkadot bridge hub", "https://polkadot-bridge-hub-rpc.polkadot.io"),
        LiveChain("kusama bridge hub", "https://kusama-bridge-hub-rpc.polkadot.io"),
        LiveChain("polkadot collectives", "https://polkadot-collectives-rpc.polkadot.io"),
        LiveChain("hydration", "https://rpc.hydradx.cloud"),
        LiveChain("astar", "https://rpc.astar.network"),
        // Listed in MetadataHashExemptions, so the app signs with Mode::Disabled. Spec 26 was built without a hash and
        // refused Mode::Enabled; spec 27 (seen 13 November 2026) accepts this app's hash, see the assertion below.
        LiveChain("pendulum", "https://rpc-pendulum.prd.pendulumchain.tech", exempt = true)
    )

    @Before
    fun requireOptIn() {
        assumeTrue("set ${LiveRpc.OPT_IN_VARIABLE}=1 to run read-only RPC checks", LiveRpc.enabled)
    }

    @Test
    fun `live runtimes accept transfers signed with the computed metadata hash`() {
        val results = chains.map { chain ->
            val result = runCatching { validate(chain) }.getOrElse { "error: ${it.message}" }
            println("${chain.name}: $result")
            chain to result
        }
        results.forEach { (chain, result) ->
            val outcome = result.substringBefore(" spec")
            if (chain.exempt) {
                // The app signs an exempt network with Mode::Disabled, which must stay accepted. Mode::Enabled is
                // refused while the runtime has no hash; a runtime built with one must accept this app's hash, never
                // answer BadProof. Then the exemption is no longer needed and can be lifted in a reviewed change.
                val accepted = setOf(
                    "enabled=$UNKNOWN_CANNOT_LOOKUP disabled=$INVALID_PAYMENT",
                    "enabled=$INVALID_PAYMENT disabled=$INVALID_PAYMENT"
                )
                assertTrue("${chain.name}: $outcome", outcome in accepted)
                if (outcome.startsWith("enabled=$INVALID_PAYMENT")) {
                    println("${chain.name}: the runtime now accepts Mode::Enabled; its MetadataHashExemptions entry can be lifted")
                }
            } else {
                assertEquals(chain.name, "enabled=$INVALID_PAYMENT", outcome)
            }
        }
    }

    private fun validate(chain: LiveChain): String {
        val versioned = rpc(chain.rpc, "state_call", "Metadata_metadata_at_version", "0x0f000000") as String
        val metadataBytes = FrameMetadataV15.unwrapVersionedResult(versioned.fromHex()) ?: error("no V15 metadata")
        val metadata = FrameMetadataV15.decode(metadataBytes)
        val properties = rpc(chain.rpc, "system_properties") as Map<*, *>
        val decimals = ((properties["tokenDecimals"] as? List<*>)?.first() ?: properties["tokenDecimals"]) as Number
        val symbol = ((properties["tokenSymbol"] as? List<*>)?.first() ?: properties["tokenSymbol"]) as String
        val hash = ChainMetadataHash.compute(metadata, decimals.toInt(), symbol)
        val version = ChainMetadataHash.runtimeVersion(metadata)
        val address = metadata.types[metadata.extrinsic.addressType.toInt()].path.joinToString("::")
        val signature = metadata.types[metadata.extrinsic.signatureType.toInt()].path.joinToString("::")
        val types = """
            {"runtime_id": 1, "types": {}, "versioning": [{"runtime_range": [0, null], "types": {
              "Balance": "u128", "Index": "u32", "Address": "$address", "ExtrinsicSignature": "$signature",
              "sp_core::crypto::AccountId32": "GenericAccountId"}}]}
        """.trimIndent()
        val runtime = ParityFixtures.buildRuntimeWithTypes(metadataBytes.toHexString(withPrefix = true), types)

        val genesis = rpc(chain.rpc, "chain_getBlockHash", 0) as String
        val finalized = rpc(chain.rpc, "chain_getFinalizedHead") as String
        val header = rpc(chain.rpc, "chain_getHeader", finalized) as Map<*, *>
        val number = Integer.decode(header["number"] as String)
        val live = rpc(chain.rpc, "state_getRuntimeVersion", finalized) as Map<*, *>
        check((live["specVersion"] as Number).toLong() == version.specVersion) { "metadata of another runtime" }
        val runtimeVersion = RuntimeVersion(version.specVersion.toInt(), version.transactionVersion.toInt())

        fun outcome(metadataHash: ByteArray?): String {
            val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32).also { SecureRandom().nextBytes(it) })
            val builder = ExtrinsicBuilder(
                runtime = runtime,
                keypair = keypair,
                nonce = BigInteger.ZERO,
                runtimeVersion = runtimeVersion,
                genesisHash = genesis.fromHex(),
                multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
                accountIdentifier = if (address.endsWith("MultiAddress")) multiAddressFromId(keypair.publicKey) else keypair.publicKey,
                blockHash = finalized.fromHex(),
                era = Era.getEraFromBlockPeriod(number, 64),
                metadataHash = metadataHash
            )
            val transfer = runtime.metadata.moduleOrNull("Balances")?.callOrNull("transfer_keep_alive")
            if (transfer != null) {
                val dest = if (address.endsWith("MultiAddress")) multiAddressFromId(ByteArray(32) { 1 }) else ByteArray(32) { 1 }
                builder.call("Balances", "transfer_keep_alive", mapOf("dest" to dest, "value" to BigInteger.TEN.pow(10)))
            } else {
                builder.call("System", "remark", mapOf("remark" to ByteArray(0)))
            }
            val extrinsic = builder.prepareSigning().sign(keypair)
            return rpc(
                chain.rpc,
                "state_call",
                "TaggedTransactionQueue_validate_transaction",
                "0x02" + extrinsic.removePrefix("0x") + finalized.removePrefix("0x"),
                finalized
            ) as String
        }

        val enabled = outcome(hash)
        val suffix = " spec ${version.specName} ${version.specVersion} hash ${hash.toHexString(withPrefix = true)}"
        return if (chain.exempt) "enabled=$enabled disabled=${outcome(null)}$suffix" else "enabled=$enabled$suffix"
    }

    private fun rpc(url: String, method: String, vararg params: Any): Any? =
        com.google.gson.Gson().fromJson(LiveRpc.call(url, method, *params), Any::class.java)

    private companion object {
        // TransactionValidity results: Err(Invalid(Payment)) and Err(Unknown(CannotLookup)).
        const val INVALID_PAYMENT = "0x010001"
        const val UNKNOWN_CANNOT_LOOKUP = "0x010100"
    }
}

package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.SecureRandom

/**
 * Read-only proof that builder output is accepted by live runtimes (manual or nightly, never in CI by default):
 * `FEARLESS_UTILS_LIVE_VALIDATE=1 ./gradlew :fearless-utils:testDebugUnitTest --tests '*LiveValidateTransactionTest*'`.
 *
 * A fresh, unfunded ED25519 key signs `transfer_keep_alive`; `TaggedTransactionQueue_validate_transaction` must answer
 * `Err(Invalid(Payment))`. That outcome is only reachable after the runtime decoded the extrinsic, every signed extension
 * and verified the signature. Missing CheckMetadataHash bytes surface as a decode failure or `Invalid(BadProof)` instead.
 * Nothing is ever submitted.
 */
class LiveValidateTransactionTest {

    private class LiveChain(val name: String, val rpc: String, val types: String)

    private val chains = listOf(
        LiveChain("polkadot", "https://rpc.polkadot.io", "parity/polkadot_parity_types.json"),
        LiveChain("kusama", "https://kusama-rpc.polkadot.io", "parity/kusama_parity_types.json"),
        LiveChain("polkadot asset hub", "https://polkadot-asset-hub-rpc.polkadot.io", "parity/statemint_parity_types.json"),
        LiveChain("kusama asset hub", "https://kusama-asset-hub-rpc.polkadot.io", "parity/statemine_parity_types.json"),
        LiveChain("westend", "https://westend-rpc.polkadot.io", "parity/westend_parity_types.json")
    )

    @Before
    fun requireOptIn() {
        assumeTrue("set ${LiveRpc.OPT_IN_VARIABLE}=1 to run read-only RPC checks", LiveRpc.enabled)
    }

    @Test
    fun `unfunded signed transfers pass decoding and signature checks on live chains`() {
        val results = chains.flatMap { chain ->
            val runtime = ParityFixtures.buildRuntime(rpc(chain.rpc, "state_getMetadata").asString, chain.types)
            val version = rpc(chain.rpc, "state_getRuntimeVersion").asJsonObject
            val runtimeVersion = RuntimeVersion(version["specVersion"].asInt, version["transactionVersion"].asInt)
            val genesis = rpc(chain.rpc, "chain_getBlockHash", 0).asString
            val finalized = rpc(chain.rpc, "chain_getFinalizedHead").asString
            val finalizedNumber = Integer.decode(rpc(chain.rpc, "chain_getHeader", finalized).asJsonObject["number"].asString)

            listOf(
                "immortal" to Pair(Era.Immortal, genesis),
                "mortal" to Pair(Era.getEraFromBlockPeriod(finalizedNumber, 64), finalized)
            ).flatMap { (eraName, eraAndBlock) ->
                val (era, eraBlock) = eraAndBlock
                listOf(false, true).map { prepared ->
                    val extrinsic = signedTransfer(runtime, runtimeVersion, genesis, era, eraBlock, prepared)
                    val validity = rpc(
                        chain.rpc,
                        "state_call",
                        "TaggedTransactionQueue_validate_transaction",
                        "0x02" + extrinsic.removePrefix("0x") + finalized.removePrefix("0x"),
                        finalized
                    ).asString
                    val label = "${chain.name} spec ${runtimeVersion.specVersion} $eraName ${if (prepared) "prepared" else "direct"}"
                    println("$label: validate_transaction = $validity (${describe(validity)})")
                    label to validity
                }
            }
        }

        results.forEach { (label, validity) -> assertEquals(label, INVALID_PAYMENT, validity) }
    }

    private fun signedTransfer(
        runtime: RuntimeSnapshot,
        runtimeVersion: RuntimeVersion,
        genesis: String,
        era: Era,
        eraBlock: String,
        prepared: Boolean
    ): String {
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32).also { SecureRandom().nextBytes(it) })
        val builder = ExtrinsicBuilder(
            runtime = runtime,
            keypair = keypair,
            nonce = BigInteger.ZERO,
            runtimeVersion = runtimeVersion,
            genesisHash = genesis.fromHex(),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = multiAddressFromId(keypair.publicKey),
            blockHash = eraBlock.fromHex(),
            era = era
        ).call(
            "Balances",
            "transfer_keep_alive",
            mapOf("dest" to multiAddressFromId(ByteArray(32) { 1 }), "value" to BigInteger.TEN.pow(10))
        )
        return if (prepared) builder.prepareSigning().sign(keypair) else builder.build()
    }

    private fun rpc(url: String, method: String, vararg params: Any) = LiveRpc.call(url, method, *params)

    private fun describe(validity: String) = when {
        validity == INVALID_PAYMENT -> "Err(Invalid(Payment)): decoded, extensions and signature accepted"
        validity == INVALID_BAD_PROOF -> "Err(Invalid(BadProof))"
        validity.startsWith("0x00") -> "Ok(ValidTransaction)"
        else -> "unexpected"
    }

    private companion object {
        // TransactionValidity = Result::Err(TransactionValidityError::Invalid(InvalidTransaction::Payment))
        const val INVALID_PAYMENT = "0x010001"
        const val INVALID_BAD_PROOF = "0x010004"
    }
}

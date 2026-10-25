package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.common.assertThrows
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.RealRuntimeProvider
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.metadata.call
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

private val ACCOUNT = ByteArray(32) { 1 }
private val AMOUNT = BigInteger.TEN.pow(10)

/**
 * Since the Asset Hub migration Polkadot and Kusama filter staking, pool, crowdloan and vesting calls at dispatch
 * (`frame_system::CallFiltered`, fee charged). With CheckMetadataHash encoded those extrinsics are valid again, so the
 * builder must refuse them before signing. Runtimes are the captured metadata in `resources/parity`.
 */
class AssetHubMigrationTest {

    private val runtimes = mutableMapOf<String, RuntimeSnapshot>()

    private fun runtime(chain: String) = runtimes.getOrPut(chain) { ParityFixtures.runtime(ParityFixtures.chain(chain)) }

    @Test
    fun `relay chains hand staking to Asset Hub and Asset Hub keeps it`() {
        listOf("polkadot", "kusama", "westend").forEach { chain ->
            assertTrue(chain, runtime(chain).metadata.isStakingMovedToAssetHub())
        }

        assertFalse(runtime("statemint").metadata.isStakingMovedToAssetHub())
        assertFalse(RealRuntimeProvider.buildRuntime("polkadot", "_v14").metadata.isStakingMovedToAssetHub())
    }

    @Test
    fun `relay staking, pool, crowdloan and vesting calls are refused before signing`() {
        val refused = listOf(
            "Staking" to listOf(
                "bond", "bond_extra", "nominate", "unbond", "withdraw_unbonded", "rebond", "set_payee",
                "set_controller", "chill", "payout_stakers", "validate"
            ),
            "NominationPools" to listOf(
                "join", "bond_extra", "unbond", "withdraw_unbonded", "claim_payout", "create", "nominate",
                "set_metadata", "chill"
            ),
            "FastUnstake" to listOf("register_fast_unstake", "deregister"),
            "VoterList" to listOf("rebag", "put_in_front_of"),
            "Crowdloan" to listOf("contribute", "contribute_all", "add_memo", "poke"),
            "Vesting" to listOf("vest", "vest_other", "vested_transfer", "merge_schedules"),
            "Claims" to listOf("attest")
        )

        listOf("polkadot", "kusama").forEach { chain ->
            refused.forEach { (module, calls) ->
                calls.forEach { call ->
                    val label = "$chain $module.$call"
                    val builder = builder(chain)

                    // Arguments never matter: the call is refused before anything is encoded or signed.
                    val error = assertThrows<CallMovedToAssetHubException> { builder.call(module, call, emptyMap()) }

                    assertEquals(label, module, error.moduleName)
                    assertEquals(label, call, error.callName)
                    assertTrue(label, runtime(chain).metadata.isCallMovedToAssetHub(module, call))
                }
            }
        }
    }

    @Test
    fun `calls are refused by runtime index too`() {
        val staking = runtime("polkadot").metadata.module("Staking")
        val (moduleIndex, callIndex) = staking.call("bond").index

        val error = assertThrows<CallMovedToAssetHubException> { builder("polkadot").call(moduleIndex, callIndex, emptyMap()) }

        assertEquals("Staking", error.moduleName)
        assertEquals("bond", error.callName)
    }

    @Test
    fun `filtered calls nested in batch or proxy arguments are refused`() {
        listOf("polkadot", "kusama").forEach { chain ->
            val chill = genericCall(chain, "Staking", "chill", emptyMap())
            val claimPayout = genericCall(chain, "NominationPools", "claim_payout", emptyMap())

            val batch = assertThrows<CallMovedToAssetHubException> {
                builder(chain).call("Utility", "batch_all", mapOf("calls" to listOf(transferCall(chain), chill)))
            }
            val proxy = assertThrows<CallMovedToAssetHubException> {
                builder(chain).call(
                    "Proxy",
                    "proxy",
                    mapOf("real" to multiAddressFromId(ACCOUNT), "force_proxy_type" to null, "call" to claimPayout)
                )
            }

            assertEquals(chain, "Staking.chill", "${batch.moduleName}.${batch.callName}")
            assertEquals(chain, "NominationPools.claim_payout", "${proxy.moduleName}.${proxy.callName}")
        }
    }

    @Test
    fun `transfers, remarks, batches and crowdloan refunds still build on migrated relay chains`() {
        listOf("polkadot", "kusama").forEach { chain ->
            val extrinsics = listOf(
                builder(chain).call("Balances", "transfer_keep_alive", transferArguments()).build(),
                builder(chain).call("Balances", "transfer_allow_death", transferArguments())
                    .call("Balances", "transfer_keep_alive", transferArguments())
                    .build(useBatchAll = true),
                builder(chain).call("System", "remark", mapOf("remark" to byteArrayOf(0))).build(),
                builder(chain).call("Utility", "batch", mapOf("calls" to listOf(transferCall(chain)))).build(),
                builder(chain).call("Crowdloan", "withdraw", mapOf("who" to ACCOUNT, "index" to PARA_ID)).build(),
                builder(chain).call("Crowdloan", "refund", mapOf("index" to PARA_ID)).build(),
                builder(chain).call("Crowdloan", "dissolve", mapOf("index" to PARA_ID)).build()
            )

            val decodedCalls = extrinsics.map { Extrinsic.fromHex(runtime(chain), it).call }
                .map { "${it.module.name}.${it.function.name}" }

            assertEquals(
                chain,
                listOf(
                    "Balances.transfer_keep_alive", "Utility.batch_all", "System.remark", "Utility.batch",
                    "Crowdloan.withdraw", "Crowdloan.refund", "Crowdloan.dissolve"
                ),
                decodedCalls
            )
        }
    }

    @Test
    fun `Asset Hub keeps building staking and pool calls`() {
        val bond = builder("statemint").call(
            "Staking",
            "bond",
            mapOf("value" to AMOUNT, "payee" to DictEnum.Entry("Staked", null))
        ).build()
        val join = builder("statemint").call(
            "NominationPools",
            "join",
            mapOf("amount" to AMOUNT, "pool_id" to BigInteger.ONE)
        ).build()

        assertEquals("bond", Extrinsic.fromHex(runtime("statemint"), bond).call.function.name)
        assertEquals("join", Extrinsic.fromHex(runtime("statemint"), join).call.function.name)
    }

    @Test
    fun `Westend migrated without RcMigrator and still dispatches vesting and crowdloans`() {
        val metadata = runtime("westend").metadata

        assertFalse(metadata.isCallMovedToAssetHub("Vesting", "vest"))
        assertFalse(metadata.isCallMovedToAssetHub("Crowdloan", "contribute"))
        assertTrue(metadata.isCallMovedToAssetHub("Staking", "bond"))

        builder("westend").call("Vesting", "vest", emptyMap()).build()
        builder("westend").call(
            "Crowdloan",
            "contribute",
            mapOf("index" to PARA_ID, "value" to AMOUNT, "signature" to null)
        ).build()
    }

    @Test
    fun `runtimes from before the migration keep staking`() {
        val runtime = RealRuntimeProvider.buildRuntime("polkadot", "_v14")
        val builder = ExtrinsicBuilder(
            runtime = runtime,
            keypair = ParityFixtures.keypair,
            nonce = BigInteger.ZERO,
            runtimeVersion = RuntimeVersion(9110, 8),
            genesisHash = ByteArray(32),
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = multiAddressFromId(ParityFixtures.keypair.publicKey)
        )

        listOf("bond", "nominate", "unbond", "chill").forEach { builder.call("Staking", it, emptyMap()) }
        builder.call("Crowdloan", "contribute", emptyMap())
        builder.call("Vesting", "vest", emptyMap())

        assertFalse(runtime.metadata.isCallMovedToAssetHub("Staking", "bond"))
    }

    private fun builder(chainName: String): ExtrinsicBuilder {
        val chain = ParityFixtures.chain(chainName)
        val genesis = chain.genesisHash.fromHex()

        return ExtrinsicBuilder(
            runtime = runtime(chainName),
            keypair = ParityFixtures.keypair,
            nonce = BigInteger.ZERO,
            runtimeVersion = RuntimeVersion(chain.specVersion, chain.transactionVersion),
            genesisHash = genesis,
            multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.ED25519),
            accountIdentifier = multiAddressFromId(ParityFixtures.keypair.publicKey)
        )
    }

    private fun genericCall(chain: String, module: String, call: String, arguments: Map<String, Any?>): GenericCall.Instance {
        val metadataModule = runtime(chain).metadata.module(module)

        return GenericCall.Instance(metadataModule, metadataModule.call(call), arguments)
    }

    private fun transferCall(chain: String) = genericCall(chain, "Balances", "transfer_keep_alive", transferArguments())

    private fun transferArguments() = mapOf("dest" to multiAddressFromId(ACCOUNT), "value" to AMOUNT)

    private companion object {
        val PARA_ID: BigInteger = BigInteger.valueOf(2000)
    }
}

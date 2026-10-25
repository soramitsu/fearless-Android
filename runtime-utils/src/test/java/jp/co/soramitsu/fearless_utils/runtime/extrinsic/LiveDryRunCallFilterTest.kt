package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toByteArray
import jp.co.soramitsu.fearless_utils.runtime.metadata.callOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

/**
 * Release gate for [isCallMovedToAssetHub] (manual or nightly, never in CI by default):
 * `FEARLESS_UTILS_LIVE_VALIDATE=1 ./gradlew :fearless-utils:testDebugUnitTest --tests '*LiveDryRunCallFilterTest*'`.
 *
 * Read-only `DryRunApi_dry_run_call` of every mutation the app offers, from a Signed origin, on each live chain:
 * the builder must refuse exactly the calls the runtime answers with `frame_system::CallFiltered`. Nothing is signed or
 * submitted. A mismatch means a runtime upgrade moved or restored a pallet and the guard table needs updating.
 */
class LiveDryRunCallFilterTest {

    private class LiveChain(val name: String, val rpc: String, val types: String)

    private class Probe(val module: String, val call: String, val arguments: Map<String, Any?>)

    private val chains = listOf(
        LiveChain("polkadot", "https://rpc.polkadot.io", "parity/polkadot_parity_types.json"),
        LiveChain("kusama", "https://kusama-rpc.polkadot.io", "parity/kusama_parity_types.json"),
        LiveChain("polkadot asset hub", "https://polkadot-asset-hub-rpc.polkadot.io", "parity/statemint_parity_types.json"),
        LiveChain("kusama asset hub", "https://kusama-asset-hub-rpc.polkadot.io", "parity/statemine_parity_types.json"),
        LiveChain("westend", "https://westend-rpc.polkadot.io", "parity/westend_parity_types.json")
    )

    private val account = ByteArray(32) { 0x11 }
    private val amount = BigInteger.TEN.pow(10)
    private val paraId = BigInteger.valueOf(2000)
    private val staked = DictEnum.Entry("Staked", null)

    private val probes = listOf(
        Probe("Staking", "bond", mapOf("value" to amount, "payee" to staked)),
        Probe("Staking", "bond_extra", mapOf("max_additional" to amount)),
        Probe("Staking", "nominate", mapOf("targets" to listOf(multiAddressFromId(account)))),
        Probe("Staking", "unbond", mapOf("value" to amount)),
        Probe("Staking", "withdraw_unbonded", mapOf("num_slashing_spans" to BigInteger.ZERO)),
        Probe("Staking", "rebond", mapOf("value" to amount)),
        Probe("Staking", "set_payee", mapOf("payee" to staked)),
        Probe("Staking", "chill", emptyMap()),
        Probe("Staking", "payout_stakers", mapOf("validator_stash" to account, "era" to BigInteger.ONE)),
        Probe("NominationPools", "join", mapOf("amount" to amount, "pool_id" to BigInteger.ONE)),
        Probe("NominationPools", "bond_extra", mapOf("extra" to DictEnum.Entry("FreeBalance", amount))),
        Probe("NominationPools", "unbond", mapOf("member_account" to multiAddressFromId(account), "unbonding_points" to amount)),
        Probe(
            "NominationPools",
            "withdraw_unbonded",
            mapOf("member_account" to multiAddressFromId(account), "num_slashing_spans" to BigInteger.ZERO)
        ),
        Probe("NominationPools", "claim_payout", emptyMap()),
        Probe("Crowdloan", "contribute", mapOf("index" to paraId, "value" to amount, "signature" to null)),
        Probe("Crowdloan", "add_memo", mapOf("index" to paraId, "memo" to byteArrayOf(0))),
        Probe("Crowdloan", "withdraw", mapOf("who" to account, "index" to paraId)),
        Probe("Crowdloan", "refund", mapOf("index" to paraId)),
        Probe("Vesting", "vest", emptyMap()),
        Probe("Balances", "transfer_keep_alive", mapOf("dest" to multiAddressFromId(account), "value" to amount)),
        Probe("System", "remark", mapOf("remark" to byteArrayOf(0)))
    )

    @Before
    fun requireOptIn() {
        assumeTrue("set ${LiveRpc.OPT_IN_VARIABLE}=1 to run read-only RPC checks", LiveRpc.enabled)
    }

    @Test
    fun `builder refuses exactly the calls live runtimes filter at dispatch`() {
        val results = chains.flatMap { chain ->
            val runtime = ParityFixtures.buildRuntime(LiveRpc.call(chain.rpc, "state_getMetadata").asString, chain.types)
            val specVersion = LiveRpc.call(chain.rpc, "state_getRuntimeVersion").asJsonObject["specVersion"].asInt

            probes.mapNotNull { probe ->
                val module = runtime.metadata.moduleOrNull(probe.module) ?: return@mapNotNull null
                val function = module.callOrNull(probe.call) ?: return@mapNotNull null
                val outcome = dryRun(chain, runtime, GenericCall.Instance(module, function, probe.arguments))
                val refused = runtime.metadata.isCallMovedToAssetHub(probe.module, probe.call)
                val label = "${chain.name} spec $specVersion ${probe.module}.${probe.call}"

                println("$label: ${outcome.description}, builder ${if (refused) "refuses" else "builds"}")
                Triple(label, outcome, refused)
            }
        }

        assertTrue("no probe reached a runtime", results.isNotEmpty())
        results.forEach { (label, outcome, refused) ->
            assertEquals(label, outcome.callFiltered, refused)
            // CallFiltered is charged: this is what the guard saves users from
            if (outcome.callFiltered) assertTrue(label, outcome.paysFee)
        }
    }

    private class DryRunOutcome(val callFiltered: Boolean, val paysFee: Boolean, val description: String)

    // DryRunApi_dry_run_call(origin: OriginCaller::system(RawOrigin::Signed(account)), call, result_xcms_version)
    private fun dryRun(chain: LiveChain, runtime: RuntimeSnapshot, call: GenericCall.Instance): DryRunOutcome {
        val params = byteArrayOf(0x00, 0x01) + account + GenericCall.toByteArray(runtime, call) + XCM_VERSION
        val result = LiveRpc.call(chain.rpc, "state_call", "DryRunApi_dry_run_call", params.toHexString(withPrefix = true))
            .asString.fromHex()

        return decodeExecutionResult(result)
    }

    // Result<CallDryRunEffects, Error>; CallDryRunEffects starts with execution_result: DispatchResultWithPostInfo
    private fun decodeExecutionResult(bytes: ByteArray): DryRunOutcome {
        require(bytes[0].toInt() == 0) { "DryRunApi error ${bytes.toHexString()}" }

        if (bytes[1].toInt() == 0) return DryRunOutcome(callFiltered = false, paysFee = true, description = "Ok")

        // DispatchErrorWithPostInfo { post_info: { actual_weight: Option<Weight>, pays_fee: Pays }, error: DispatchError }
        var offset = 2
        if (bytes[offset++].toInt() == 1) {
            offset = skipCompact(bytes, offset)
            offset = skipCompact(bytes, offset)
        }
        val paysFee = bytes[offset++].toInt() == 0
        val error = bytes.copyOfRange(offset, minOf(offset + 6, bytes.size))
        val callFiltered = error.contentEquals(CALL_FILTERED)

        return DryRunOutcome(
            callFiltered = callFiltered,
            paysFee = paysFee,
            description = "Err(${if (callFiltered) "CallFiltered" else error.toHexString(withPrefix = true)}, paysFee=$paysFee)"
        )
    }

    private fun skipCompact(bytes: ByteArray, offset: Int): Int {
        val first = bytes[offset].toInt() and 0xff

        return offset + when (first and 0b11) {
            0 -> 1
            1 -> 2
            2 -> 4
            else -> (first shr 2) + 5
        }
    }

    private companion object {
        val XCM_VERSION = byteArrayOf(4, 0, 0, 0)

        // DispatchError::Module(ModuleError { index: 0 (System), error: [5 (CallFiltered), 0, 0, 0] })
        val CALL_FILTERED = byteArrayOf(3, 0, 5, 0, 0, 0)
    }
}

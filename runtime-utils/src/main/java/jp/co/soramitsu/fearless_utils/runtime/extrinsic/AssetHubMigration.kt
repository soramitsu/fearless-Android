package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull

// Relay-chain pallets of the 2025 Asset Hub migration
private const val RC_MIGRATOR = "RcMigrator"
private const val STAKING_AH_CLIENT = "StakingAhClient"

private const val CROWDLOAN = "Crowdloan"

private val STAKING_MODULES = setOf("Staking", "NominationPools", "FastUnstake", "VoterList")

// Other pallets whose state RcMigrator moved; Westend migrated without RcMigrator and still dispatches them.
private val MIGRATED_MODULES = setOf("Vesting", "Claims", CROWDLOAN)

// Releasing contributions of ended crowdloans is the only crowdloan action left on the relay chain.
private val CROWDLOAN_CALLS_KEPT_ON_RELAY = setOf("withdraw", "refund", "dissolve")

/**
 * Thrown by [ExtrinsicBuilder] before anything is signed. The relay chain still decodes and includes such a call,
 * charges its fee and then rejects it at dispatch with `frame_system::CallFiltered`; the state it would act on
 * (stake, pool membership, vesting, contributions) lives on the chain's Asset Hub now.
 */
class CallMovedToAssetHubException(
    val moduleName: String,
    val callName: String
) : Exception("$moduleName.$callName moved to Asset Hub: the relay chain charges the fee and filters the call")

/**
 * True for relay chains that hand staking to their Asset Hub (Polkadot, Kusama, Westend).
 * Asset Hub runtimes carry `StakingRcClient` instead and keep dispatching Staking and NominationPools.
 */
fun RuntimeMetadata.isStakingMovedToAssetHub(): Boolean =
    moduleOrNull(RC_MIGRATOR) != null || moduleOrNull(STAKING_AH_CLIENT) != null

/**
 * Whether this runtime filters [moduleName].[callName] at dispatch because the pallet moved to Asset Hub.
 * Verified with read-only `DryRunApi_dry_run_call` on Polkadot 2005000, Kusama 2003002 and Westend 1025001.
 */
fun RuntimeMetadata.isCallMovedToAssetHub(moduleName: String, callName: String): Boolean = when (moduleName) {
    in STAKING_MODULES -> isStakingMovedToAssetHub()
    CROWDLOAN -> hasRcMigrator() && callName !in CROWDLOAN_CALLS_KEPT_ON_RELAY
    in MIGRATED_MODULES -> hasRcMigrator()
    else -> false
}

/**
 * @throws CallMovedToAssetHubException if [call], or a call nested in its arguments (batch, proxy),
 * would be filtered at dispatch
 */
internal fun RuntimeMetadata.requireNotMovedToAssetHub(call: GenericCall.Instance) {
    val moved = firstCallMovedToAssetHub(call) ?: return

    throw CallMovedToAssetHubException(moved.module.name, moved.function.name)
}

private fun RuntimeMetadata.firstCallMovedToAssetHub(value: Any?): GenericCall.Instance? = when (value) {
    is GenericCall.Instance -> value.takeIf { isCallMovedToAssetHub(it.module.name, it.function.name) }
        ?: firstCallMovedToAssetHub(value.arguments)
    is DictEnum.Entry<*> -> firstCallMovedToAssetHub(value.value)
    is Struct.Instance -> firstCallMovedToAssetHub(value.mapping)
    is Map<*, *> -> value.values.firstNotNullOfOrNull { firstCallMovedToAssetHub(it) }
    is Iterable<*> -> value.firstNotNullOfOrNull { firstCallMovedToAssetHub(it) }
    else -> null
}

private fun RuntimeMetadata.hasRcMigrator() = moduleOrNull(RC_MIGRATOR) != null

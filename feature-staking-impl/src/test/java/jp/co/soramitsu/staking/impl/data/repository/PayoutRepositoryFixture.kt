package jp.co.soramitsu.staking.impl.data.repository

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.bytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u128
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntryModifier
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageHasher
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntryType
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.ChainService
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.staking.api.domain.model.Nominations
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.api.domain.model.ValidatorPrefs
import jp.co.soramitsu.staking.impl.data.network.subquery.SubQueryValidatorSetFetcher
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * One unclaimed era (9) of a legacy (`ErasStakersClipped`) relay chain, read through the real
 * [PayoutRepository], SCALE bindings and storage keys. Only the node and the storage cache are mocked.
 *
 * Defaults: era reward 10 000 planks, all 100 points earned by the validator, 10% commission,
 * exposure 1 000 of which the validator owns 100 and the nominator 900.
 */
internal class PayoutRepositoryFixture {
    private val runtime = LegacyStakingRuntime()
    private val staking = mock<StakingRelayChainScenarioRepository>()
    private val bulk = mock<BulkRetriever>()
    private val history = mock<SubQueryValidatorSetFetcher>()
    private val cache = mock<StorageCache>()
    private val registry = mock<ChainRegistry>()
    private val socket = mock<SocketService>()
    private val provider = mock<RuntimeProvider>()
    private val connection = mock<ChainConnection> { on { socketService } doReturn socket }
    private val chain = mock<Chain> {
        on { id } doReturn CHAIN_ID
        on { addressPrefix } doReturn 42
        on { ecosystem } doReturn Ecosystem.Substrate
    }
    private val owner = ByteArray(32) { 1 }
    private val validator = ByteArray(32) { 2 }
    private val controller = ByteArray(32) { 3 }
    private val remote = mutableMapOf<String, String?>()
    private val local = mutableMapOf<String, String?>()
    private val repository = PayoutRepository(staking, bulk, history, cache, registry)

    val validatorAddress = chain.addressOf(validator)
    val era: BigInteger = BigInteger.valueOf(9)

    fun nominator() = StakingState.Stash.Nominator(
        chain, owner, controller, owner, Nominations(listOf(validator), BigInteger.ONE, false)
    )

    fun validator() = StakingState.Stash.Validator(
        chain, validator, controller, validator, ValidatorPrefs(BigDecimal("0.1"), false)
    )

    suspend fun start() {
        whenever(registry.getService(CHAIN_ID)).thenReturn(ChainService(provider, connection))
        whenever(provider.get()).thenReturn(runtime.snapshot)
        whenever(staking.getActiveEraIndex(CHAIN_ID)).thenReturn(BigInteger.TEN)
        whenever(staking.getCurrentEraIndex(CHAIN_ID)).thenReturn(BigInteger.TEN)
        whenever(staking.getHistoryDepth(CHAIN_ID)).thenReturn(BigInteger.ONE)
        whenever(history.fetchAllValidators(CHAIN_ID, chain.addressOf(owner))).thenReturn(listOf(validatorAddress))
        whenever(bulk.queryKeys(eq(socket), any(), anyOrNull())).thenAnswer { call ->
            call.getArgument<List<String>>(1).associateWith { remote[it] }
        }
        whenever(cache.getEntries(any<List<String>>(), eq(CHAIN_ID))).thenAnswer { call ->
            call.getArgument<List<String>>(0).map { StorageEntry(it, local[it]) }
        }
        put("Bonded", controller, validator)
        put(
            "Ledger",
            record(
                "stash" to validator,
                "total" to BigInteger.valueOf(1_000),
                "active" to BigInteger.valueOf(1_000),
                "unlocking" to emptyList<Any>(),
                "claimedRewards" to emptyList<BigInteger>()
            ),
            controller
        )
        exposure()
        prefs()
        reward()
        points()
    }

    fun exposure(
        total: BigInteger = BigInteger.valueOf(1_000),
        own: BigInteger = BigInteger.valueOf(100),
        stake: BigInteger = BigInteger.valueOf(900)
    ) = put(
        "ErasStakersClipped",
        record("total" to total, "own" to own, "others" to listOf(record("who" to owner, "value" to stake))),
        era,
        validator
    )

    /** [commission] in parts per billion, as stored on chain. */
    fun prefs(commission: BigInteger = BigInteger.valueOf(100_000_000)) =
        put("ErasValidatorPrefs", record("commission" to commission, "blocked" to false), era, validator)

    fun reward(value: BigInteger = BigInteger.valueOf(10_000)) = put("ErasValidatorReward", value, era)

    fun points(total: BigInteger = BigInteger.valueOf(100), earned: BigInteger = BigInteger.valueOf(100)) =
        put("ErasRewardPoints", record("total" to total, "individual" to listOf(listOf(validator, earned))), era)

    suspend fun payouts(state: StakingState.Stash) = repository.calculateUnpaidPayouts(state)

    private fun put(name: String, value: Any, vararg arguments: Any) {
        // Era rewards and points are read through the storage cache; the rest comes from the node.
        val target = if (name == "ErasValidatorReward" || name == "ErasRewardPoints") local else remote
        target[runtime.key(name, *arguments)] = runtime.encode(name, value)
    }

    private companion object {
        const val CHAIN_ID = "legacy-payout"
    }
}

/** The `Staking` storage entries the payout calculation reads, with identity-hashed keys. */
private class LegacyStakingRuntime {
    private val account = FixedByteArray("AccountId32", 32)
    private val individual = structure("IndividualExposure", "who" to account, "value" to u128)
    private val unlock = structure("UnlockChunk", "value" to u128, "era" to u32)
    private val point = Tuple("IndividualPoint", listOf(TypeReference(account), TypeReference(u32)))
    private val types = mapOf(
        "Bonded" to account,
        "Ledger" to structure(
            "Ledger",
            "stash" to account,
            "total" to u128,
            "active" to u128,
            "unlocking" to Vec("Unlocks", TypeReference(unlock)),
            "claimedRewards" to Vec("Eras", TypeReference(u32))
        ),
        "ErasStakersClipped" to structure(
            "Exposure",
            "total" to u128,
            "own" to u128,
            "others" to Vec("Others", TypeReference(individual))
        ),
        "ErasValidatorPrefs" to structure("Prefs", "commission" to u32, "blocked" to BooleanType),
        "ErasValidatorReward" to u128,
        "ErasRewardPoints" to structure(
            "Points",
            "total" to u32,
            "individual" to Vec("Individuals", TypeReference(point))
        )
    )
    private val entries = types.mapValues { (name, type) ->
        val keys = when (name) {
            "Bonded", "Ledger" -> listOf(account)
            "ErasStakersClipped", "ErasValidatorPrefs" -> listOf(u32, account)
            else -> listOf(u32)
        }
        jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry(
            name,
            StorageEntryModifier.Optional,
            StorageEntryType.NMap(keys, keys.map { StorageHasher.Identity }, type),
            byteArrayOf(),
            emptyList(),
            "Staking"
        )
    }
    private val pallet =
        Module("Staking", Storage("Staking", entries), null, null, emptyMap(), emptyMap(), BigInteger.ZERO)

    val snapshot = RuntimeSnapshot(
        mock<TypeRegistry>(),
        RuntimeMetadata(BigInteger.ZERO, mapOf("Staking" to pallet), ExtrinsicMetadata(BigInteger.ZERO, emptyList()))
    )

    fun key(name: String, vararg arguments: Any) = entries.getValue(name).storageKey(snapshot, *arguments)

    fun encode(name: String, value: Any) = types.getValue(name).bytes(snapshot, value).toHexString(true)

    private fun structure(name: String, vararg fields: Pair<String, Type<*>>) =
        Struct(name, linkedMapOf(*fields.map { it.first to TypeReference(it.second) }.toTypedArray()))
}

private fun record(vararg fields: Pair<String, Any?>) = Struct.Instance(mapOf(*fields))

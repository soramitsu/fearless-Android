package jp.co.soramitsu.staking.impl.scenarios.parachain

import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.common.data.network.runtime.binding.BinderWithKey
import jp.co.soramitsu.common.data.network.runtime.binding.BlockHash
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Null
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u128
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntryModifier
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageHasher
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntryType
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.runtime.storage.source.ChildKeyBuilder
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.StorageKey
import jp.co.soramitsu.staking.api.domain.model.CandidateCapacity
import jp.co.soramitsu.staking.api.domain.model.CandidateInfoStatus
import jp.co.soramitsu.staking.api.domain.model.DelegationAction
import jp.co.soramitsu.staking.api.domain.model.DelegatorStateStatus
import jp.co.soramitsu.staking.api.domain.model.Round
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parachain staking storage over a small ParachainStaking pallet with Moonbeam-shaped values: the real repository
 * builds keys and binds values from an in-memory storage that holds SCALE values of the same pallet.
 */
class StakingParachainScenarioRepositoryCoverageTest {

    private val account20 = FixedByteArray("AccountId20", 20)
    private val bond = Struct("Bond", linkedMapOf("owner" to ref(account20), "amount" to ref(u128)))
    private val bonds = Vec("Bonds", ref(bond))
    private val delegatorStatus = DictEnum("DelegatorStatus", listOf(DictEnum.Entry("Active", ref(Null)), DictEnum.Entry("Leaving", ref(u32))))
    private val capacity = DictEnum(
        "CapacityStatus",
        listOf(DictEnum.Entry("Full", ref(Null)), DictEnum.Entry("Empty", ref(Null)), DictEnum.Entry("Partial", ref(Null)))
    )
    private val collatorStatus = DictEnum(
        "CollatorStatus",
        listOf(DictEnum.Entry("Active", ref(Null)), DictEnum.Entry("Idle", ref(Null)), DictEnum.Entry("Leaving", ref(u32)))
    )
    private val action = DictEnum("DelegationAction", listOf(DictEnum.Entry("Revoke", ref(u128)), DictEnum.Entry("Decrease", ref(u128))))
    private val request = Struct(
        "ScheduledRequest",
        linkedMapOf("delegator" to ref(account20), "whenExecutable" to ref(u32), "action" to ref(action))
    )
    private val candidate = Struct(
        "CandidateMetadata",
        linkedMapOf(
            "bond" to ref(u128),
            "delegationCount" to ref(u32),
            "totalCounted" to ref(u128),
            "lowestTopDelegationAmount" to ref(u128),
            "highestBottomDelegationAmount" to ref(u128),
            "lowestBottomDelegationAmount" to ref(u128),
            "topCapacity" to ref(capacity),
            "bottomCapacity" to ref(capacity),
            "status" to ref(collatorStatus)
        )
    )

    private val module = Module(
        "ParachainStaking",
        Storage(
            "ParachainStaking",
            mapOf(
                entry(
                    "DelegatorState",
                    Struct(
                        "Delegator",
                        linkedMapOf("id" to ref(account20), "delegations" to ref(bonds), "total" to ref(u128), "status" to ref(delegatorStatus))
                    ),
                    account20
                ),
                entry("AtStake", Struct("Snapshot", linkedMapOf("bond" to ref(u128), "delegations" to ref(bonds), "total" to ref(u128))), u32, account20),
                entry("Total", u128),
                entry("CandidateInfo", candidate, account20),
                entry("Round", Struct("RoundInfo", linkedMapOf("current" to ref(u32), "first" to ref(u32), "length" to ref(u32)))),
                entry("SelectedCandidates", Vec("Selected", ref(account20))),
                entry("DelegationScheduledRequests", Vec("Requests", ref(request)), account20),
                entry("BottomDelegations", Struct("Delegations", linkedMapOf("delegations" to ref(bonds), "total" to ref(u128))), account20)
            )
        ),
        emptyMap(),
        emptyMap(),
        emptyMap(),
        emptyMap(),
        BigInteger.valueOf(20)
    )
    private val runtime = snapshot(mapOf("ParachainStaking" to module))
    private val emptyRuntime = snapshot(emptyMap())

    private val delegatorId = ByteArray(20) { 1 }
    private val collator = ByteArray(20) { 2 }
    private val other = ByteArray(20) { 3 }
    private val chain = StakingLaneFixtures.chain()

    private val remote = FakeStorage(runtime)
    private val local = FakeStorage(runtime)
    private val repository = StakingParachainScenarioRepository(remote, local)

    private fun ref(type: Type<*>) = TypeReference(type)

    private fun entry(name: String, value: Type<*>, vararg keys: Type<*>): Pair<String, StorageEntry> {
        val type = if (keys.isEmpty()) {
            StorageEntryType.Plain(value)
        } else {
            StorageEntryType.NMap(keys.toList(), keys.map { StorageHasher.Twox64Concat }, value)
        }
        return name to StorageEntry(name, StorageEntryModifier.Optional, type, ByteArray(0), emptyList(), "ParachainStaking")
    }

    private fun snapshot(modules: Map<String, Module>) = RuntimeSnapshot(
        mockk(relaxed = true),
        RuntimeMetadata(BigInteger.valueOf(14), modules, ExtrinsicMetadata(BigInteger.valueOf(4), emptyList()))
    )

    private fun key(name: String, vararg arguments: Any?) = module.storage(name).storageKey(runtime, *arguments)

    @Suppress("UNCHECKED_CAST")
    private fun hexOf(name: String, value: Any?): String = (module.storage(name).type.value as Type<Any?>).toHex(runtime, value)

    private fun bondOf(owner: ByteArray, amount: Long) = Struct.Instance(mapOf("owner" to owner, "amount" to BigInteger.valueOf(amount)))

    private fun candidateOf(status: DictEnum.Entry<Any?>) = Struct.Instance(
        mapOf(
            "bond" to BigInteger.valueOf(2_000),
            "delegationCount" to BigInteger.valueOf(4),
            "totalCounted" to BigInteger.valueOf(5_000),
            "lowestTopDelegationAmount" to BigInteger.TEN,
            "highestBottomDelegationAmount" to BigInteger.ONE,
            "lowestBottomDelegationAmount" to BigInteger.ONE,
            "topCapacity" to DictEnum.Entry("Full", null),
            "bottomCapacity" to DictEnum.Entry("Partial", null),
            "status" to status
        )
    )

    private fun requestOf(amount: Long) = Struct.Instance(
        mapOf("delegator" to delegatorId, "whenExecutable" to BigInteger.valueOf(12), "action" to DictEnum.Entry("Decrease", BigInteger.valueOf(amount)))
    )

    @Test
    fun `delegator state is observed remotely and read locally`() = runTest {
        val state = hexOf(
            "DelegatorState",
            Struct.Instance(
                mapOf(
                    "id" to delegatorId,
                    "delegations" to listOf(bondOf(collator, 500)),
                    "total" to BigInteger.valueOf(500),
                    "status" to DictEnum.Entry("Active", null)
                )
            )
        )
        remote.values[key("DelegatorState", delegatorId)] = state
        local.values[key("DelegatorState", delegatorId)] = state

        val delegator = repository.stakingStateFlow(chain, delegatorId).first() as StakingState.Parachain.Delegator
        val delegation = delegator.delegations.single()

        assertArrayEquals(collator, delegation.collatorId)
        assertEquals(BigInteger.valueOf(500), delegation.delegatedAmountInPlanks)
        assertEquals(DelegatorStateStatus.ACTIVE, delegation.status)
        assertEquals(0, BigDecimal(500).compareTo(delegator.totalDelegatedAmount))
        assertEquals(BigInteger.valueOf(500), repository.getDelegatorState(CHAIN, delegatorId)!!.total)
        assertTrue(repository.stakingStateFlow(chain, collator).first() is StakingState.Parachain.None)
        assertNull(repository.getDelegatorState(CHAIN, collator))
    }

    @Test
    fun `collator snapshot total and round are bound`() = runTest {
        val atStakeValue = Struct.Instance(
            mapOf("bond" to BigInteger.valueOf(1_000), "delegations" to listOf(bondOf(delegatorId, 300)), "total" to BigInteger.valueOf(1_300))
        )
        val round = Struct.Instance(mapOf("current" to FIVE, "first" to BigInteger.valueOf(100), "length" to BigInteger.TEN))
        remote.values[key("AtStake", FIVE, collator)] = hexOf("AtStake", atStakeValue)
        remote.values[key("Total")] = hexOf("Total", BigInteger.valueOf(9_000))
        remote.values[key("Round")] = hexOf("Round", round)

        val atStake = repository.getAtStakeOfCollator(CHAIN, collator, FIVE)

        assertEquals(BigInteger.valueOf(1_000), atStake.bond)
        assertEquals(BigInteger.valueOf(1_300), atStake.total)
        assertArrayEquals(delegatorId, atStake.delegations.single().first)
        assertEquals(BigInteger.valueOf(300), atStake.delegations.single().second)
        assertEquals(BigInteger.valueOf(9_000), repository.total(CHAIN))
        assertEquals(Round(FIVE, BigInteger.valueOf(100), BigInteger.TEN), repository.getCurrentRound(CHAIN))
    }

    @Test
    fun `absent required values are incompatible`() = runTest {
        assertTrue(runCatching { repository.getAtStakeOfCollator(CHAIN, collator, FIVE) }.isFailure)
        assertTrue(runCatching { repository.total(CHAIN) }.isFailure)
        assertTrue(runCatching { repository.getCurrentRound(CHAIN) }.isFailure)
        assertTrue(runCatching { repository.getCandidateInfo(CHAIN, collator) }.isFailure)
        assertNull(repository.getSelectedCandidates(CHAIN))
    }

    @Test
    fun `candidate infos are keyed by collator hex`() = runTest {
        remote.values[key("CandidateInfo", collator)] = hexOf("CandidateInfo", candidateOf(DictEnum.Entry("Active", null)))
        remote.values[key("CandidateInfo", other)] = hexOf("CandidateInfo", candidateOf(DictEnum.Entry("Leaving", BigInteger.valueOf(55))))

        val infos = repository.getCandidateInfos(CHAIN, listOf(collator, other))
        val active = infos.getValue(collator.toHexString())

        assertTrue(repository.getCandidateInfos(CHAIN, emptyList()).isEmpty())
        assertEquals(BigInteger.valueOf(2_000), active.bond)
        assertEquals(BigInteger.valueOf(4), active.delegationCount)
        assertEquals(CandidateCapacity.Full, active.topCapacity)
        assertEquals(CandidateCapacity.Partial, active.bottomCapacity)
        assertEquals(CandidateInfoStatus.ACTIVE, active.status)
        assertEquals(55L, (repository.getCandidateInfo(CHAIN, other).status as CandidateInfoStatus.LEAVING).leavingBlock)
    }

    @Test
    fun `selected candidates are listed`() = runTest {
        remote.values[key("SelectedCandidates")] = hexOf("SelectedCandidates", listOf(collator, other))

        val selected = repository.getSelectedCandidates(CHAIN)!!

        assertEquals(2, selected.size)
        assertArrayEquals(other, selected.last())
    }

    @Test
    fun `scheduled requests are read by collator`() = runTest {
        remote.values[key("DelegationScheduledRequests", collator)] = hexOf("DelegationScheduledRequests", listOf(requestOf(40)))

        val request = repository.getDelegationScheduledRequests(CHAIN, collator).single()
        val byCollator = repository.getScheduledRequests(CHAIN, listOf(collator, other))

        assertArrayEquals(delegatorId, request.delegator)
        assertEquals(BigInteger.valueOf(12), request.whenExecutable)
        assertEquals(DelegationAction.UNSTAKE, request.action)
        assertEquals(BigInteger.valueOf(40), request.actionValue)
        assertEquals(1, repository.getDelegationScheduledRequestsFlow(CHAIN, collator).first().size)
        assertTrue(repository.getDelegationScheduledRequests(CHAIN, other).isEmpty())
        assertEquals(1, byCollator.getValue(collator.toHexString()).size)
        assertTrue(byCollator.getValue(other.toHexString()).isEmpty())
    }

    @Test
    fun `bottom delegations are keyed by collator hex`() = runTest {
        val bottom = Struct.Instance(mapOf("delegations" to listOf(bondOf(delegatorId, 7)), "total" to BigInteger.valueOf(7)))
        remote.values[key("BottomDelegations", collator)] = hexOf("BottomDelegations", bottom)

        val delegations = repository.getBottomDelegations(CHAIN, listOf(collator, other))

        assertArrayEquals(delegatorId, delegations.getValue(collator.toHexString()).single().owner)
        assertEquals(BigInteger.valueOf(7), delegations.getValue(collator.toHexString()).single().amount)
        assertTrue(delegations.getValue(other.toHexString()).isEmpty())
        assertTrue(bindDelegations("0x00", emptyRuntime).isEmpty())
    }

    @Test
    fun `runtime without parachain staking has no delegator`() = runTest {
        val bare = StakingParachainScenarioRepository(FakeStorage(emptyRuntime), FakeStorage(emptyRuntime))

        assertTrue(bare.stakingStateFlow(chain, delegatorId).first() is StakingState.Parachain.None)
        assertNull(bare.getDelegatorState(CHAIN, delegatorId))
        assertTrue(bare.getDelegationScheduledRequestsFlow(CHAIN, collator).first().isEmpty())
        assertTrue(runCatching { bare.total(CHAIN) }.isFailure)
    }

    /** In-memory storage: builds keys and binds values with the given runtime, as the real sources do. */
    private class FakeStorage(private val runtime: RuntimeSnapshot) : StorageDataSource {
        val values = mutableMapOf<String, String?>()

        private fun valueOf(key: String?) = key?.let { values[it] }

        override suspend fun <T> query(chainId: String, keyBuilder: (RuntimeSnapshot) -> StorageKey?, at: BlockHash?, binding: Binder<T>): T =
            binding(valueOf(keyBuilder(runtime)), runtime)

        override suspend fun <K, T> queryKeys(
            chainId: String,
            keysBuilder: (RuntimeSnapshot) -> Map<StorageKey, K>,
            at: BlockHash?,
            binding: Binder<T>
        ): Map<K, T> = keysBuilder(runtime).entries.associate { (key, argument) -> argument to binding(values[key], runtime) }

        override fun <T> observe(chainId: String, keyBuilder: (RuntimeSnapshot) -> StorageKey?, binder: Binder<T>): Flow<T> =
            flow { emit(binder(valueOf(keyBuilder(runtime)), runtime)) }

        override suspend fun <T> queryResult(chainId: String, keyBuilder: (RuntimeSnapshot) -> StorageKey?, at: BlockHash?, binding: Binder<T>): Result<T> =
            runCatching { query(chainId, keyBuilder, at, binding) }

        override fun <T> observeResult(chainId: String, keyBuilder: (RuntimeSnapshot) -> StorageKey?, binder: Binder<T>): Flow<Result<T>> =
            observe(chainId, keyBuilder, binder).map { Result.success(it) }

        override suspend fun <K, T> queryByPrefix(
            chainId: String,
            prefixKeyBuilder: (RuntimeSnapshot) -> StorageKey?,
            keyExtractor: (String) -> K,
            binding: BinderWithKey<T, K>
        ): Map<K, T> = error("Prefix reads are not used by the parachain repository")

        override suspend fun <T> queryChildState(
            chainId: String,
            storageKeyBuilder: (RuntimeSnapshot) -> StorageKey,
            childKeyBuilder: ChildKeyBuilder,
            binder: Binder<T>
        ): T = error("Child state is not read by the parachain repository")
    }

    private companion object {
        const val CHAIN = "parachain"
        val FIVE: BigInteger = BigInteger.valueOf(5)
    }
}

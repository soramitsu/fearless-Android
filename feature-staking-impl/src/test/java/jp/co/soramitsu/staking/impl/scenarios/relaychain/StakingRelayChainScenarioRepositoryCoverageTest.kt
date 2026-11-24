package jp.co.soramitsu.staking.impl.scenarios.relaychain

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.common.data.network.runtime.binding.BinderWithKey
import jp.co.soramitsu.common.data.network.runtime.binding.BlockHash
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.session
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.coredb.dao.AccountStakingDao
import jp.co.soramitsu.coredb.model.AccountStakingLocal
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.storage.source.ChildKeyBuilder
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.StorageKey
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.activeEraStorageKeyOrNull
import jp.co.soramitsu.staking.impl.data.repository.AssetHubMetadataContractTest
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The relay staking repository over the Polkadot Asset Hub runtime metadata: storage keys are built and values bound
 * by the real code, from an in-memory storage that answers with SCALE values encoded by the same runtime.
 */
class StakingRelayChainScenarioRepositoryCoverageTest {

    private val runtime: RuntimeSnapshot = AssetHubMetadataContractTest.runtimes.getValue("polkadot")
    private val emptyRuntime = RuntimeSnapshot(mockk(relaxed = true), RuntimeMetadata(BigInteger.ONE, emptyMap(), ExtrinsicMetadata(BigInteger.valueOf(4), emptyList())))
    private val staking = runtime.metadata.staking()
    private val hub = AssetHubStaking.POLKADOT
    private val relay = "relay-chain"
    private val validator = ByteArray(32) { 0x22 }
    private val stash = ByteArray(32) { 0x11 }
    private val controller = ByteArray(32) { 0x33 }
    private val chain = StakingLaneFixtures.chain(StakingLaneFixtures.chainAsset(chainId = relay))

    private val vectors: JsonObject = javaClass.getResourceAsStream("/asset-hub/polkadot-vectors.json")!!.reader().use {
        Gson().fromJson(it, JsonObject::class.java)
    }

    private val remote = FakeStorage(runtime)
    private val local = FakeStorage(runtime)
    private val registry = mockk<ChainRegistry>()
    private val walletConstants = mockk<WalletConstants>()
    private val accountStakingDao = mockk<AccountStakingDao>()
    private val storageCache = mockk<StorageCache>()
    private val repository = StakingRelayChainScenarioRepository(remote, local, registry, walletConstants, accountStakingDao, storageCache)

    init {
        coEvery { registry.getRuntime(any()) } returns runtime
        coEvery { registry.getRuntimeOrNull(any()) } returns runtime
    }

    private fun vector(name: String) = vectors[name].asString

    @Suppress("UNCHECKED_CAST")
    private fun encode(module: Module, name: String, value: Any): String = (module.storage(name).returnType() as Type<Any>).toHex(runtime, value)

    private val activeEraKey get() = requireNotNull(runtime.metadata.activeEraStorageKeyOrNull())

    private fun key(name: String, vararg arguments: Any?) = staking.storage(name).storageKey(runtime, *arguments)

    private fun stubPagedExposure() {
        local.values[activeEraKey] = vector("activeEra")
        local.values[key("ErasStakersOverview", BigInteger.valueOf(2310), validator)] = vector("exposureOverview")
        local.values[key("ErasStakersPaged", BigInteger.valueOf(2310), validator, BigInteger.ZERO)] = vector("exposurePage")
    }

    @Test
    fun `hub active era is an acknowledged read`() = runTest {
        remote.values[activeEraKey] = vector("activeEra")
        assertEquals(BigInteger.valueOf(2310), repository.getActiveEraIndex(hub))

        remote.values.remove(activeEraKey)
        assertTrue(runCatching { repository.getActiveEraIndex(hub) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `relay eras come from the local cache`() = runTest {
        local.values[activeEraKey] = vector("activeEra")
        local.values[staking.storage("CurrentEra").storageKey()] = encode(staking, "CurrentEra", BigInteger.valueOf(2311))

        assertEquals(BigInteger.valueOf(2310), repository.getActiveEraIndex(relay))
        assertEquals(BigInteger.valueOf(2311), repository.getCurrentEraIndex(relay))
        assertEquals(BigInteger.valueOf(2310), repository.observeActiveEraIndex(relay).first())
    }

    @Test
    fun `absent active era is not observed`() = runTest {
        assertTrue(repository.observeActiveEraIndex(relay).toList().isEmpty())
    }

    @Test
    fun `hub clock values come from the runtime`() = runTest {
        remote.values[staking.storage("BondedEras").storageKey()] = vector("bondedEras")
        val session = runtime.metadata.session()
        remote.values[session.storage("CurrentIndex").storageKey()] = encode(session, "CurrentIndex", BigInteger.valueOf(77))

        assertEquals(BigInteger.valueOf(13_859), repository.eraStartSessionIndex(hub, BigInteger.valueOf(2310)))
        assertEquals(BigInteger.valueOf(77), repository.currentSessionIndex(hub))
        assertEquals(staking.numberConstant("SessionsPerEra", runtime), repository.eraLength(hub))
        // The Hub has no HistoryDepth storage: the constant is used.
        assertEquals(staking.numberConstant("HistoryDepth", runtime), repository.getHistoryDepth(hub))
    }

    @Test
    fun `nomination pool pallet id decodes to its bytes`() = runTest {
        assertEquals("py/nopls", String(repository.getNominationPoolPalletId(hub)))
    }

    @Test
    fun `minimum nominator bond is at least the existential deposit`() = runTest {
        val dot = StakingLaneFixtures.chainAsset(chainId = relay)
        coEvery { walletConstants.existentialDeposit(dot) } returns BigInteger.TEN
        remote.values[staking.storage("MinNominatorBond").storageKey()] = encode(staking, "MinNominatorBond", BigInteger.valueOf(500))
        assertEquals(BigInteger.valueOf(500), repository.minimumNominatorBond(dot))

        remote.values.clear()
        assertEquals(BigInteger.TEN, repository.minimumNominatorBond(dot))
        coEvery { walletConstants.existentialDeposit(dot) } returns null
        assertEquals(BigInteger.ZERO, repository.minimumNominatorBond(dot))
    }

    @Test
    fun `optional staking counters are read when present`() = runTest {
        assertNull(repository.maxNominators(hub))
        assertNull(repository.minimumActiveStake(hub))
        assertNull(repository.nominatorsCount(hub))

        remote.values[staking.storage("MaxNominatorsCount").storageKey()] = encode(staking, "MaxNominatorsCount", BigInteger.valueOf(50_000))
        remote.values[staking.storage("MinimumActiveStake").storageKey()] = encode(staking, "MinimumActiveStake", BigInteger.valueOf(123))
        remote.values[staking.storage("CounterForNominators").storageKey()] = encode(staking, "CounterForNominators", BigInteger.valueOf(42))

        assertEquals(BigInteger.valueOf(50_000), repository.maxNominators(hub))
        assertEquals(BigInteger.valueOf(123), repository.minimumActiveStake(hub))
        assertEquals(BigInteger.valueOf(42), repository.nominatorsCount(hub))
    }

    @Test
    fun `paged exposures are joined with their nominator pages`() = runTest {
        stubPagedExposure()

        val direct = repository.getLegacyElectedValidatorsExposure(relay, BigInteger.valueOf(2310))
        val observed = repository.legacyElectedExposuresInActiveEra(relay).first()
        val active = repository.getLegacyActiveElectedValidatorsExposures(relay)

        assertFalse(repository.isLegacyErasStakersSchema(relay))
        for (exposures in listOf(direct, observed, active)) {
            val exposure = exposures.getValue(validator.toHexString())
            assertEquals(BigInteger.valueOf(1_000), exposure.total)
            assertEquals(BigInteger.valueOf(100), exposure.own)
            assertArrayEquals(stash, exposure.others.single().who)
            assertEquals(BigInteger.valueOf(900), exposure.others.single().value)
        }
    }

    @Test
    fun `runtime without paged exposures reads the legacy storage`() = runTest {
        coEvery { registry.getRuntime("legacy") } returns emptyRuntime
        val legacy = StakingRelayChainScenarioRepository(FakeStorage(emptyRuntime), FakeStorage(emptyRuntime), registry, walletConstants, accountStakingDao, storageCache)

        assertTrue(legacy.isLegacyErasStakersSchema("legacy"))
        assertTrue(legacy.getLegacyElectedValidatorsExposure("legacy", BigInteger.ONE).isEmpty())
        assertTrue(legacy.getValidatorPrefs("legacy", listOf(validator.toHexString())).isEmpty())
        assertTrue(legacy.getAllValidatorPrefs("legacy").isEmpty())
    }

    @Test
    fun `validator preferences are read by id and from the cache`() = runTest {
        remote.values[key("Validators", validator)] = vector("validatorPrefs")
        local.values[key("Validators", validator)] = vector("validatorPrefs")
        val other = ByteArray(32) { 0x44 }.toHexString()

        val byId = repository.getValidatorPrefs(relay, listOf(validator.toHexString(), other))
        val cached = repository.getAllValidatorPrefs(relay)

        assertEquals(0, byId.getValue(validator.toHexString())!!.commission.compareTo(BigDecimal("0.1")))
        assertNull(byId[other])
        assertEquals(0, cached.getValue(validator.toHexString())!!.commission.compareTo(BigDecimal("0.1")))
    }

    @Test
    fun `hub unbonding period follows slashability and recent validation`() = runTest {
        remote.values[activeEraKey] = vector("activeEra")
        val full = staking.numberConstant("BondingDuration", runtime)
        val fast = staking.numberConstant("NominatorFastUnbondDuration", runtime)

        assertEquals(full, repository.assetHubUnbondingEras(hub, stash))

        remote.values[staking.storage("AreNominatorsSlashable").storageKey()] = encode(staking, "AreNominatorsSlashable", false)
        assertEquals(fast, repository.assetHubUnbondingEras(hub, stash))

        remote.values[key("LastValidatorEra", stash)] = encode(staking, "LastValidatorEra", BigInteger.valueOf(2309))
        assertEquals(full, repository.assetHubUnbondingEras(hub, stash))
        assertTrue(runCatching { repository.assetHubUnbondingEras(relay, stash) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `unbonding duration rules`() {
        val full = BigInteger.valueOf(28)
        val fast = BigInteger.TWO
        assertEquals(fast, assetHubUnbondingDuration(full, fast, false, BigInteger.valueOf(100), BigInteger.valueOf(50)))
        assertEquals(full, assetHubUnbondingDuration(full, fast, false, BigInteger.valueOf(100), BigInteger.valueOf(90)))
        assertEquals(full, assetHubUnbondingDuration(full, fast, true, BigInteger.valueOf(100), null))
        assertTrue(runCatching { assetHubUnbondingDuration(fast, full, false, BigInteger.ONE, null) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `hub has no slashing spans`() = runTest {
        assertNull(repository.getSlashingSpan(hub, stash))
    }

    @Test
    fun `reward destination is read from the cached payee`() = runTest {
        local.values[key("Payee", stash)] = vector("payeeStaked")
        val state = StakingState.Stash.None(chain, stash, stash, stash)

        assertSame(RewardDestination.Restake, repository.getRewardDestination(state))
    }

    @Test
    fun `staking state follows the access info and remote role`() = runTest {
        val asset = chain.assets.first()
        fun access(info: AccountStakingLocal.AccessInfo?) {
            every { accountStakingDao.observeDistinct(relay, asset.id, stash) } returns flowOf(AccountStakingLocal(relay, asset.id, stash, info))
        }

        access(null)
        assertTrue(repository.stakingStateFlow(chain, asset, stash).first() is StakingState.NonStash)

        access(AccountStakingLocal.AccessInfo(stash, controller))
        assertTrue(repository.stakingStateFlow(chain, asset, stash).first() is StakingState.Stash.None)

        remote.values[key("Nominators", stash)] = vector("nominations")
        val nominator = repository.stakingStateFlow(chain, asset, stash).first() as StakingState.Stash.Nominator
        assertEquals(2, nominator.nominations.targets.size)
        assertArrayEquals(controller, nominator.controllerId)

        remote.values[key("Validators", stash)] = vector("validatorPrefs")
        assertTrue(repository.stakingStateFlow(chain, asset, stash).first() is StakingState.Stash.Validator)
    }

    @Test
    fun `nominations are read and observed with their result`() = runTest {
        remote.values[key("Nominators", stash)] = vector("nominations")

        val nominations = repository.getRemoteAccountNominations(relay, stash)!!
        val observed = repository.observeRemoteAccountNominationsResult(relay, stash).first().getOrThrow()!!

        assertEquals(BigInteger.valueOf(2300), nominations.submittedInEra)
        assertEquals(nominations.targets.map { it.toList() }, observed.targets.map { it.toList() })
        assertNull(repository.getRemoteAccountNominations(relay, controller))
    }

    @Test
    fun `ledgers are keyed by the controller`() = runTest {
        local.values[key("Ledger", controller)] = vector("ledger")
        remote.values[key("Ledger", controller)] = vector("ledger")
        val state = StakingState.Stash.None(chain, stash, controller, stash)

        assertEquals(BigInteger.valueOf(12_000), repository.ledgerFlow(state).first().active)
        assertEquals(BigInteger.valueOf(12_345), repository.ledger(relay, chain.addressOf(controller))!!.total)
        assertNull(repository.ledger(relay, chain.addressOf(stash)))
        assertTrue(repository.ledgerFlow(StakingState.Stash.None(chain, stash, stash, stash)).toList().isEmpty())
    }

    @Test
    fun `era validator rewards are keyed by era`() = runTest {
        remote.values[key("ErasValidatorReward", BigInteger.valueOf(2309))] = encode(staking, "ErasValidatorReward", BigInteger.valueOf(1_000))

        assertEquals(mapOf(BigInteger.valueOf(2309) to BigInteger.valueOf(1_000)), repository.getErasValidatorRewards(hub))
    }

    @Test
    fun `era points are read from the cache over the history depth`() = runTest {
        remote.values[activeEraKey] = vector("activeEra")
        local.values[staking.storage("CurrentEra").storageKey()] = encode(staking, "CurrentEra", BigInteger.valueOf(2311))
        coEvery { storageCache.getEntries(any<List<String>>(), hub) } returns emptyList()
        val depth = staking.numberConstant("HistoryDepth", runtime).toInt()

        assertTrue(repository.retrieveEraPointsDistribution(hub).isEmpty())
        assertEquals((2311 - depth until 2310).map { BigInteger.valueOf(it.toLong()) }, repository.historicalEras(hub))
        coVerify { storageCache.getEntries(match<List<String>> { it.size == 2310 - (2311 - depth) }, hub) }
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
        ): Map<K, T> {
            val prefix = prefixKeyBuilder(runtime) ?: return emptyMap()
            return values.filterKeys { it.startsWith(prefix) }.entries.associate { (key, scale) ->
                val extracted = keyExtractor(key)
                extracted to binding(scale, runtime, extracted)
            }
        }

        override suspend fun <T> queryChildState(
            chainId: String,
            storageKeyBuilder: (RuntimeSnapshot) -> StorageKey,
            childKeyBuilder: ChildKeyBuilder,
            binder: Binder<T>
        ): T = error("Child state is not read by the staking repository")
    }
}

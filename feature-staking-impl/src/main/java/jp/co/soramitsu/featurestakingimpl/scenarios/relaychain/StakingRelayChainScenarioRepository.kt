package jp.co.soramitsu.featurestakingimpl.scenarios.relaychain

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.NonNullBinderWithType
import jp.co.soramitsu.common.data.network.runtime.binding.returnType
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.accountIdFromMapKey
import jp.co.soramitsu.common.utils.babe
import jp.co.soramitsu.common.utils.constant
import jp.co.soramitsu.common.utils.mapValuesNotNull
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.session
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.common.utils.stakingOrNull
import jp.co.soramitsu.common.utils.storageKeys
import jp.co.soramitsu.coredb.dao.AccountStakingDao
import jp.co.soramitsu.coredb.model.AccountStakingLocal
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageOrNull
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.featurestakingapi.domain.api.AccountIdMap
import jp.co.soramitsu.featurestakingapi.domain.model.EraIndex
import jp.co.soramitsu.featurestakingapi.domain.model.Exposure
import jp.co.soramitsu.featurestakingapi.domain.model.Nominations
import jp.co.soramitsu.featurestakingapi.domain.model.SlashingSpans
import jp.co.soramitsu.featurestakingapi.domain.model.StakingLedger
import jp.co.soramitsu.featurestakingapi.domain.model.StakingState
import jp.co.soramitsu.featurestakingapi.domain.model.ValidatorPrefs
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindActiveEra
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindCurrentEra
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindCurrentIndex
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindCurrentSlot
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindErasStartSessionIndex
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindExposure
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindHistoryDepth
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindMaxNominators
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindMinBond
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindNominations
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindNominatorsCount
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindRewardDestination
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindSlashDeferDuration
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindSlashingSpans
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindStakingLedger
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings.bindValidatorPrefs
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.updaters.activeEraStorageKeyOrNull
import jp.co.soramitsu.featurewalletapi.domain.interfaces.WalletConstants
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.observeNonNull
import jp.co.soramitsu.runtime.storage.source.queryNonNull
import kotlin.math.floor
import kotlin.math.max
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

class StakingRelayChainScenarioRepository(
    private val remoteStorage: StorageDataSource,
    private val localStorage: StorageDataSource,
    private val chainRegistry: ChainRegistry,
    private val walletConstants: WalletConstants,
    private val accountStakingDao: AccountStakingDao
) {
    suspend fun sessionLength(chainId: ChainId): BigInteger {
        val runtime = runtimeFor(chainId)

        return runtime.metadata.babe().numberConstant("EpochDuration", runtime) // How many blocks per session
    }

    suspend fun currentSessionIndex(chainId: ChainId) = remoteStorage.queryNonNull(
        // Current session index
        keyBuilder = { it.metadata.session().storage("CurrentIndex").storageKey() },
        binding = ::bindCurrentIndex,
        chainId = chainId
    )

    suspend fun currentSlot(chainId: ChainId) = remoteStorage.queryNonNull(
        keyBuilder = { it.metadata.babe().storage("CurrentSlot").storageKey() },
        binding = ::bindCurrentSlot,
        chainId = chainId
    )

    suspend fun genesisSlot(chainId: ChainId) = remoteStorage.queryNonNull(
        keyBuilder = { it.metadata.babe().storage("GenesisSlot").storageKey() },
        binding = ::bindCurrentSlot,
        chainId = chainId
    )

    suspend fun eraStartSessionIndex(chainId: ChainId, currentEra: BigInteger): EraIndex {
        val runtime = runtimeFor(chainId)
        return remoteStorage.queryNonNull( // Index of session from with the era started
            keyBuilder = { it.metadata.staking().storage("ErasStartSessionIndex").storageKey(runtime, currentEra) },
            binding = ::bindErasStartSessionIndex,
            chainId = chainId
        )
    }

    suspend fun eraLength(chainId: ChainId): BigInteger {
        val runtime = runtimeFor(chainId)

        return runtime.metadata.staking().numberConstant("SessionsPerEra", runtime) // How many sessions per era
    }

    suspend fun blockCreationTime(chainId: ChainId): BigInteger {
        val runtime = runtimeFor(chainId)

        return runtime.metadata.babe().numberConstant("ExpectedBlockTime", runtime)
    }

    suspend fun getActiveEraIndex(chainId: ChainId): EraIndex = localStorage.queryNonNull(
        keyBuilder = { it.metadata.activeEraStorageKeyOrNull() },
        binding = ::bindActiveEra,
        chainId = chainId
    )

    suspend fun getCurrentEraIndex(chainId: ChainId): EraIndex = localStorage.queryNonNull(
        keyBuilder = { it.metadata.staking().storage("CurrentEra").storageKey() },
        binding = ::bindCurrentEra,
        chainId = chainId
    )

    suspend fun getHistoryDepth(chainId: ChainId): BigInteger = localStorage.queryNonNull(
        keyBuilder = { it.metadata.staking().storage("HistoryDepth").storageKey() },
        binding = ::bindHistoryDepth,
        chainId = chainId
    )

    fun observeActiveEraIndex(chainId: String): Flow<BigInteger> {
        return localStorage.observeNonNull(
            chainId = chainId,
            keyBuilder = { it.metadata.activeEraStorageKeyOrNull() },
            binding = { scale, runtime -> bindActiveEra(scale, runtime) }
        )
    }

    fun electedExposuresInActiveEra(chainId: ChainId) = observeActiveEraIndex(chainId).mapLatest {
        getElectedValidatorsExposure(chainId, it)
    }.runCatching { this }.getOrDefault(emptyFlow())

    private suspend fun getElectedValidatorsExposure(chainId: ChainId, eraIndex: EraIndex): Map<String, Exposure> = localStorage.queryByPrefix(
        chainId = chainId,
        prefixKeyBuilder = { it.metadata.moduleOrNull(Modules.STAKING)?.storage("ErasStakers")?.storageKey(it, eraIndex) },
        keyExtractor = { it.accountIdFromMapKey() }
    ) { scale, runtime, _ ->
        val storageType = runtime.metadata.staking().storage("ErasStakers").returnType()
        bindExposure(scale!!, runtime, storageType)
    }.mapValuesNotNull { it.value }

    suspend fun getValidatorPrefs(
        chainId: ChainId,
        accountIdsHex: List<String>
    ): AccountIdMap<ValidatorPrefs?> {
        return remoteStorage.queryKeys(
            keysBuilder = { runtime ->
                val storage = runtime.metadata.stakingOrNull()?.storage("Validators") ?: return@queryKeys emptyMap()

                accountIdsHex.associateBy { accountIdHex -> storage.storageKey(runtime, accountIdHex.fromHex()) }
            },
            binding = { scale, runtime ->
                val storageType = runtime.metadata.staking().storage("Validators").returnType()
                scale?.let { bindValidatorPrefs(scale, runtime, storageType) }
            },
            chainId = chainId
        )
    }

    suspend fun getSlashes(chainId: ChainId, accountIdsHex: List<String>): AccountIdMap<Boolean> = withContext(Dispatchers.Default) {
        val runtime = runtimeFor(chainId)

        val storage = runtime.metadata.staking().storage("SlashingSpans")

        val activeEraIndex = getActiveEraIndex(chainId)

        val returnType = storage.type.value!!

        val slashDeferDurationConstant = runtime.metadata.staking().constant("SlashDeferDuration")
        val slashDeferDuration = bindSlashDeferDuration(slashDeferDurationConstant, runtime)

        val accountIds = accountIdsHex.map { it.fromHex() }

        remoteStorage.queryKeys(
            keysBuilder = {
                storage.storageKeys(
                    runtime = runtime,
                    singleMapArguments = accountIds,
                    argumentTransform = { it.toHexString() }
                )
            },
            binding = { scale, _ ->
                val span = scale?.let { bindSlashingSpans(it, runtime, returnType) }

                isSlashed(span, activeEraIndex, slashDeferDuration)
            },
            chainId = chainId
        )
    }

    private fun isSlashed(
        slashingSpans: SlashingSpans?,
        activeEraIndex: BigInteger,
        slashDeferDuration: BigInteger
    ) = slashingSpans != null && activeEraIndex - slashingSpans.lastNonZeroSlash < slashDeferDuration

    suspend fun getSlashingSpan(chainId: ChainId, accountId: AccountId): SlashingSpans? {
        return remoteStorage.query(
            keyBuilder = { it.metadata.staking().storage("SlashingSpans").storageKey(it, accountId) },
            binding = { scale, runtimeSnapshot -> scale?.let { bindSlashingSpans(it, runtimeSnapshot) } },
            chainId = chainId
        )
    }

    suspend fun getRewardDestination(stakingState: StakingState.Stash) = localStorage.queryNonNull(
        keyBuilder = { it.metadata.staking().storage("Payee").storageKey(it, stakingState.stashId) },
        binding = { scale, runtime -> bindRewardDestination(scale, runtime, stakingState.stashId, stakingState.controllerId) },
        chainId = stakingState.chain.id
    )

    suspend fun minimumNominatorBond(chainId: ChainId): BigInteger {
        val minBond = queryStorageIfExists(
            storageName = "MinNominatorBond",
            binder = ::bindMinBond,
            chainId = chainId
        ) ?: BigInteger.ZERO

        val existentialDeposit = walletConstants.existentialDeposit(chainId)

        return minBond.max(existentialDeposit)
    }

    suspend fun maxNominators(chainId: ChainId): BigInteger? = queryStorageIfExists(
        storageName = "MaxNominatorsCount",
        binder = ::bindMaxNominators,
        chainId = chainId
    )

    suspend fun nominatorsCount(chainId: ChainId): BigInteger? = queryStorageIfExists(
        storageName = "CounterForNominators",
        binder = ::bindNominatorsCount,
        chainId = chainId
    )

    private suspend fun <T> queryStorageIfExists(
        chainId: ChainId,
        storageName: String,
        binder: NonNullBinderWithType<T>
    ): T? {
        val runtime = runtimeFor(chainId)

        return runtime.metadata.staking().storageOrNull(storageName)?.let { storageEntry ->
            localStorage.query(
                keyBuilder = { storageEntry.storageKey() },
                binding = { scale, _ -> scale?.let { binder(scale, runtime, storageEntry.returnType()) } },
                chainId = chainId
            )
        }
    }

    suspend fun stakingStateFlow(
        chain: Chain,
        chainAsset: Chain.Asset,
        accountId: AccountId
    ): Flow<StakingState> {
        return accountStakingDao.observeDistinct(chain.id, chainAsset.id, accountId)
            .flatMapLatest { accountStaking ->
                accountStaking.stakingAccessInfo?.let { accessInfo ->
                    observeStashState(chain, accessInfo, accountId)
                } ?: flowOf(StakingState.NonStash(chain, accountStaking.accountId))
            }
    }

    private fun observeStashState(
        chain: Chain,
        accessInfo: AccountStakingLocal.AccessInfo,
        accountId: AccountId
    ): Flow<StakingState.Stash> {
        val stashId = accessInfo.stashId
        val controllerId = accessInfo.controllerId

        return combine(
            observeAccountNominations(chain.id, stashId),
            observeAccountValidatorPrefs(chain.id, stashId)
        ) { nominations, prefs ->
            when {
                prefs != null -> StakingState.Stash.Validator(chain, accountId, controllerId, stashId, prefs)
                nominations != null -> StakingState.Stash.Nominator(chain, accountId, controllerId, stashId, nominations)
                else -> StakingState.Stash.None(chain, accountId, controllerId, stashId)
            }
        }
    }

    private fun observeAccountValidatorPrefs(chainId: ChainId, stashId: AccountId): Flow<ValidatorPrefs?> {
        return localStorage.observe(
            chainId = chainId,
            keyBuilder = { it.metadata.staking().storage("Validators").storageKey(it, stashId) },
            binder = { scale, runtime ->
                val storageType = runtime.metadata.staking().storage("Validators").returnType()
                scale?.let { bindValidatorPrefs(it, runtime, storageType) }
            }
        )
    }

    private fun observeAccountNominations(chainId: ChainId, stashId: AccountId): Flow<Nominations?> {
        return localStorage.observe(
            chainId = chainId,
            keyBuilder = { it.metadata.staking().storage("Nominators").storageKey(it, stashId) },
            binder = { scale, runtime -> scale?.let { bindNominations(it, runtime) } }
        )
    }

    suspend fun ledgerFlow(stakingState: StakingState.Stash): Flow<StakingLedger> {
        return localStorage.observe(
            keyBuilder = { it.metadata.staking().storage("Ledger").storageKey(it, stakingState.controllerId) },
            binder = { scale, runtime -> scale?.let { bindStakingLedger(it, runtime) } },
            chainId = stakingState.chain.id
        ).filterNotNull()
    }

    suspend fun ledger(chainId: ChainId, address: String) = remoteStorage.query(
        keyBuilder = { it.metadata.staking().storage("Ledger").storageKey(it, address.toAccountId()) },
        binding = { scale, runtime -> scale?.let { bindStakingLedger(it, runtime) } },
        chainId = chainId
    )

    private suspend fun runtimeFor(chainId: String) = chainRegistry.getRuntime(chainId)
}

suspend fun StakingRelayChainScenarioRepository.historicalEras(chainId: ChainId): List<BigInteger> {
    val activeEra = getActiveEraIndex(chainId).toInt()
    val currentEra = getCurrentEraIndex(chainId).toInt()
    val historyDepth = getHistoryDepth(chainId).toInt()

    val historicalRange = max((currentEra - historyDepth), 0) until activeEra

    return historicalRange.map(Int::toBigInteger)
}

suspend fun StakingRelayChainScenarioRepository.erasPerDay(chainId: ChainId): Int {
    val blockCreationTime = blockCreationTime(chainId)
    val sessionPerEra = eraLength(chainId)
    val blocksPerSession = sessionLength(chainId)

    val eraDuration = (blockCreationTime * sessionPerEra * blocksPerSession).toDouble()

    val dayDuration = 1.toDuration(DurationUnit.DAYS).toDouble(DurationUnit.MILLISECONDS)

    return floor(dayDuration / eraDuration).toInt()
}

suspend fun StakingRelayChainScenarioRepository.getActiveElectedValidatorsExposures(chainId: ChainId) =
    electedExposuresInActiveEra(chainId).firstOrNull() ?: emptyMap()

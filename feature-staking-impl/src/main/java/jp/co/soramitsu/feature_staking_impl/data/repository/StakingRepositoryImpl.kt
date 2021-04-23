package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.common.data.network.runtime.binding.NonNullBinder
import jp.co.soramitsu.common.data.network.runtime.binding.bindAccountInfo
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.constant
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.model.AccountStakingLocal
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Election
import jp.co.soramitsu.feature_staking_api.domain.model.Nominations
import jp.co.soramitsu.feature_staking_api.domain.model.StakingLedger
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.StakingStory
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.SlashingSpan
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindActiveEra
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindCurrentEra
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindElectionFromPhase
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindElectionFromStatus
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindExposure
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindHistoryDepth
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindNominations
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindRewardDestination
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindSlashDeferDuration
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindSlashingSpans
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindStakingLedger
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindTotalInsurance
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.activeEraStorageKey
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.observeActiveEraIndex
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.StakingStoriesDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigInteger

class StakingRepositoryImpl(
    private val storageCache: StorageCache,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val accountStakingDao: AccountStakingDao,
    private val bulkRetriever: BulkRetriever,
    private val stakingStoriesDataSource: StakingStoriesDataSource,
) : StakingRepository {

    override suspend fun electionFlow(networkType: Node.NetworkType): Flow<Election> {
        val runtime = runtimeProperty.get()

        val electionNewStorage = runtime.metadata.moduleOrNull("ElectionProviderMultiPhase")?.storage("CurrentPhase")

        val electionStorage = electionNewStorage ?: runtime.metadata.staking().storage("CurrentPhase")

        return storageCache.observeEntry(electionStorage.storageKey(), networkType)
            .map {
                val content = it.content!!

                if (electionNewStorage != null) {
                    bindElectionFromPhase(content, runtime)
                } else {
                    bindElectionFromStatus(content, runtime)
                }
            }
    }

    override suspend fun getLockupPeriodInDays(networkType: Node.NetworkType): Int {
        val runtime = runtimeProperty.get()

        val inEras = runtime.metadata.staking().numberConstant("BondingDuration", runtime)

        return inEras.toInt() / networkType.runtimeConfiguration.erasPerDay
    }

    override suspend fun getTotalIssuance(): BigInteger = getFromStorageNonNull(
        keyBuilder = { it.metadata.balances().storage("TotalIssuance").storageKey() },
        binding = ::bindTotalInsurance
    )

    override suspend fun getActiveEraIndex(): BigInteger = getFromStorageNonNull(
        keyBuilder = { it.metadata.activeEraStorageKey() },
        binding = ::bindActiveEra
    )

    override suspend fun getCurrentEraIndex(): BigInteger = getFromStorageNonNull(
        keyBuilder = { it.metadata.staking().storage("CurrentEra").storageKey() },
        binding = ::bindCurrentEra
    )

    override suspend fun getHistoryDepth(): BigInteger = getFromStorageNonNull(
        keyBuilder = { it.metadata.staking().storage("HistoryDepth").storageKey() },
        binding = ::bindHistoryDepth
    )

    override suspend fun observeActiveEraIndex(networkType: Node.NetworkType): Flow<BigInteger> {
        return storageCache.observeActiveEraIndex(runtimeProperty.get(), networkType)
    }

    override suspend fun getElectedValidatorsExposure(eraIndex: BigInteger) = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val prefixKey = runtime.metadata.staking().storage("ErasStakers").storageKey(runtime, eraIndex)

        storageCache.getEntries(prefixKey).associate {
            val accountId = it.storageKey.accountIdFromMapKey()

            accountId to bindExposure(it.content!!, runtime)
        }
    }

    override suspend fun getElectedValidatorsPrefs(eraIndex: BigInteger) = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val prefixKey = runtime.metadata.staking().storage("ErasValidatorPrefs").storageKey(runtime, eraIndex)

        storageCache.getEntries(prefixKey).associate {
            val accountId = it.storageKey.accountIdFromMapKey()

            accountId to bindValidatorPrefs(it.content!!, runtime)
        }
    }

    override suspend fun getSlashes(accountIdsHex: List<String>) = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val storage = runtime.metadata.staking().storage("SlashingSpans")
        val fullKeys = storage.accountMapStorageKeys(runtime, accountIdsHex)

        val activeEraIndex = getActiveEraIndex()

        val returnType = storage.type.value!!

        val slashDeferDurationConstant = runtime.metadata.staking().constant("SlashDeferDuration")
        val slashDeferDuration = bindSlashDeferDuration(slashDeferDurationConstant, runtime)

        bulkRetriever.queryKeys(fullKeys)
            .mapKeys { (fullKey, _) -> fullKey.accountIdFromMapKey() }
            .mapValues { (_, value) ->
                val span = value?.let { bindSlashingSpans(it, runtime, returnType) }

                isSlashed(span, activeEraIndex, slashDeferDuration)
            }
    }

    override fun stakingStateFlow(accountAddress: String): Flow<StakingState> {
        return accountStakingDao.observeDistinct(accountAddress)
            .flatMapLatest { accountStaking ->
                val accessInfo = accountStaking.stakingAccessInfo

                if (accessInfo == null) {
                    flowOf(StakingState.NonStash(accountStaking.address))
                } else {
                    observeStashState(accessInfo, accountAddress)
                }
            }
    }

    override suspend fun getRewardDestination(stakingState: StakingState.Stash) = withContext(Dispatchers.Default) {
        val runtime = runtimeProperty.get()
        val storageKey = runtime.metadata.staking().storage("Payee").storageKey(runtime, stakingState.stashId)

        val rewardDestinationEncoded = storageCache.getEntry(storageKey).content!!

        bindRewardDestination(rewardDestinationEncoded, runtime, stakingState.stashId, stakingState.controllerId)
    }

    override suspend fun getControllerAccountInfo(stakingState: StakingState.Stash): AccountInfo {
        return getFromStorage(
            keyBuilder = { it.metadata.system().storage("Account").storageKey(it, stakingState.stashId) },
            binding = { scale, runtime -> scale?.let { bindAccountInfo(it, runtime) } ?: AccountInfo.empty() }
        )
    }

    override fun stakingStoriesFlow(): Flow<List<StakingStory>> {
        return stakingStoriesDataSource.getStoriesFlow()
    }

    override suspend fun ledgerFlow(stakingState: StakingState.Stash): Flow<StakingLedger> {
        return observeStorage(
            networkType = stakingState.controllerAddress.networkType(),
            keyBuilder = { it.metadata.staking().storage("Ledger").storageKey(it, stakingState.controllerId) },
            binder = { scale, runtime -> scale?.let { bindStakingLedger(it, runtime) } }
        ).filterNotNull()
    }

    private suspend fun observeStashState(
        accessInfo: AccountStakingLocal.AccessInfo,
        accountAddress: String,
    ): Flow<StakingState.Stash> {
        val networkType = accountAddress.networkType()
        val stashId = accessInfo.stashId
        val controllerId = accessInfo.controllerId

        return combine(
            observeAccountNominations(stashId, networkType),
            observeAccountValidatorPrefs(stashId, networkType)
        ) { nominations, prefs ->
            when {
                prefs != null -> StakingState.Stash.Validator(
                    accountAddress, controllerId, stashId, prefs
                )
                nominations != null -> StakingState.Stash.Nominator(
                    accountAddress, controllerId, stashId, nominations
                )

                else -> StakingState.Stash.None(accountAddress, controllerId, stashId)
            }
        }
    }

    private suspend fun observeAccountValidatorPrefs(
        stashId: AccountId,
        networkType: Node.NetworkType,
    ): Flow<ValidatorPrefs?> {
        return observeStorage(
            networkType = networkType,
            keyBuilder = { it.metadata.staking().storage("Validators").storageKey(it, stashId) },
            binder = { scale, runtime ->
                scale?.let { bindValidatorPrefs(it, runtime) }
            }
        )
    }

    private suspend fun observeAccountNominations(
        stashId: AccountId,
        networkType: Node.NetworkType,
    ): Flow<Nominations?> {
        return observeStorage(
            networkType = networkType,
            keyBuilder = { it.metadata.staking().storage("Nominators").storageKey(it, stashId) },
            binder = { scale, runtime -> scale?.let { bindNominations(it, runtime) } }
        )
    }

    private fun isSlashed(
        span: SlashingSpan?,
        activeEraIndex: BigInteger,
        slashDeferDuration: BigInteger,
    ) = span != null && activeEraIndex - span.lastNonZeroSlash < slashDeferDuration

    private suspend fun getRuntime() = runtimeProperty.get()

    private suspend fun <T> getFromStorage(
        keyBuilder: (RuntimeSnapshot) -> String,
        binding: Binder<T>,
    ): T = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val scale = storageCache.getEntry(keyBuilder(runtime)).content

        binding(scale, runtime)
    }

    private suspend inline fun <T> getFromStorageNonNull(
        noinline keyBuilder: (RuntimeSnapshot) -> String,
        crossinline binding: NonNullBinder<T>,
    ) = getFromStorage(keyBuilder) { scale, runtime -> binding(scale!!, runtime) }

    private suspend fun <T> observeStorage(
        networkType: Node.NetworkType,
        keyBuilder: (RuntimeSnapshot) -> String,
        binder: Binder<T>,
    ): Flow<T> = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        storageCache.observeEntry(keyBuilder(runtime), networkType)
            .map { binder(it.content, runtime) }
    }
}

package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.NonNullBinderWithType
import jp.co.soramitsu.common.data.network.runtime.binding.bindAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.bindBlockNumber
import jp.co.soramitsu.common.data.network.runtime.binding.returnType
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.babe
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.constant
import jp.co.soramitsu.common.utils.hasModule
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.session
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.model.AccountStakingLocal
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageOrNull
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.Nominations
import jp.co.soramitsu.feature_staking_api.domain.model.SlashingSpans
import jp.co.soramitsu.feature_staking_api.domain.model.StakingLedger
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.domain.model.StakingStory
import jp.co.soramitsu.feature_staking_api.domain.model.ValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindActiveEra
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindCurrentEra
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindCurrentIndex
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindCurrentSlot
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindErasStartSessionIndex
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindExposure
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindHistoryDepth
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindMaxNominators
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindMinBond
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindNominations
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindNominatorsCount
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindRewardDestination
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindSlashDeferDuration
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindSlashingSpans
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindStakingLedger
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindTotalInsurance
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindValidatorPrefs
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.activeEraStorageKey
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.observeActiveEraIndex
import jp.co.soramitsu.feature_staking_impl.data.repository.datasource.StakingStoriesDataSource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.queryNonNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.math.BigInteger

class StakingRepositoryImpl(
    private val storageCache: StorageCache,
    private val accountRepository: AccountRepository,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val accountStakingDao: AccountStakingDao,
    private val bulkRetriever: BulkRetriever,
    private val remoteStorage: StorageDataSource,
    private val localStorage: StorageDataSource,
    private val walletConstants: WalletConstants,
    private val stakingStoriesDataSource: StakingStoriesDataSource
) : StakingRepository {

    override suspend fun eraLeftTime(destinationEra: BigInteger): BigInteger {
        val runtime = runtimeProperty.get()

        val sessionLength = runtime.metadata.babe().numberConstant("EpochDuration", runtime) // How many blocks per session
        val eraLength = runtime.metadata.staking().numberConstant("SessionsPerEra", runtime) // How many sessions per era

        val currentSessionIndex = localStorage.queryNonNull( // Current session index
            keyBuilder = { it.metadata.session().storage("CurrentIndex").storageKey() },
            binding = ::bindCurrentIndex
        )

        val currentSlot = localStorage.queryNonNull(
            keyBuilder = { it.metadata.babe().storage("CurrentSlot").storageKey() },
            binding = ::bindCurrentSlot
        )

        val genesisSlot = localStorage.queryNonNull(
            keyBuilder = { it.metadata.babe().storage("GenesisSlot").storageKey() },
            binding = ::bindCurrentSlot
        )

        val era = getCurrentEraIndex()
        val eraStartSessionIndex = // Индекс сессии когда началась эра
            remoteStorage.queryNonNull( // FIXME -> localStorage, I added Updater, but it doesn't work // Index of session from with the era started
                keyBuilder = { it.metadata.staking().storage("ErasStartSessionIndex").storageKey(runtime, era) },
                binding = ::bindErasStartSessionIndex
            )

        val sessionStartSlot = currentSessionIndex * sessionLength + genesisSlot
        val sessionProgress = currentSlot - sessionStartSlot
        val eraProgress = (currentSessionIndex - eraStartSessionIndex) * sessionLength + sessionProgress
        val eraRemained = eraLength * sessionLength - eraProgress

        val blockCreationTime = runtime.metadata.babe().numberConstant("ExpectedBlockTime", runtime) // How many time each block takes

        return eraRemained * blockCreationTime
    }

    override fun stakingAvailableFlow() = runtimeProperty.observe().map { it.metadata.hasModule(Modules.STAKING) }

    override suspend fun getTotalIssuance(): BigInteger = localStorage.queryNonNull(
        keyBuilder = { it.metadata.balances().storage("TotalIssuance").storageKey() },
        binding = ::bindTotalInsurance
    )

    override suspend fun getActiveEraIndex(): BigInteger = localStorage.queryNonNull(
        keyBuilder = { it.metadata.activeEraStorageKey() },
        binding = ::bindActiveEra
    )

    override suspend fun getCurrentEraIndex(): BigInteger = localStorage.queryNonNull(
        keyBuilder = { it.metadata.staking().storage("CurrentEra").storageKey() },
        binding = ::bindCurrentEra
    )

    override suspend fun getHistoryDepth(): BigInteger = localStorage.queryNonNull(
        keyBuilder = { it.metadata.staking().storage("HistoryDepth").storageKey() },
        binding = ::bindHistoryDepth
    )

    override suspend fun observeActiveEraIndex(networkType: Node.NetworkType): Flow<BigInteger> {
        return storageCache.observeActiveEraIndex(runtimeProperty.get(), networkType)
    }

    override val electedExposuresInActiveEra by lazy { createExposuresFlow() }

    override suspend fun getElectedValidatorsExposure(eraIndex: BigInteger) = withContext(Dispatchers.Default) {
        val runtime = getRuntime()

        val prefixKey = runtime.metadata.staking().storage("ErasStakers").storageKey(runtime, eraIndex)

        storageCache.getEntries(prefixKey).associate {
            val accountId = it.storageKey.accountIdFromMapKey()

            accountId to bindExposure(it.content!!, runtime)
        }
    }

    override suspend fun getValidatorPrefs(
        accountIdsHex: List<String>,
    ): AccountIdMap<ValidatorPrefs?> {
        return remoteStorage.queryKeys(
            keysBuilder = { runtime ->
                val storage = runtime.metadata.staking().storage("Validators")

                accountIdsHex.associateBy { accountIdHex -> storage.storageKey(runtime, accountIdHex.fromHex()) }
            },
            binding = { scale, runtime ->
                scale?.let { bindValidatorPrefs(scale, runtime) }
            }
        )
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

    override suspend fun getSlashingSpan(accountId: AccountId): SlashingSpans? {
        return remoteStorage.query(
            keyBuilder = { it.metadata.staking().storage("SlashingSpans").storageKey(it, accountId) },
            binding = { scale, runtimeSnapshot -> scale?.let { bindSlashingSpans(it, runtimeSnapshot) } }
        )
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

    override suspend fun getRewardDestination(stakingState: StakingState.Stash) = localStorage.queryNonNull(
        keyBuilder = { it.metadata.staking().storage("Payee").storageKey(it, stakingState.stashId) },
        binding = { scale, runtime -> bindRewardDestination(scale, runtime, stakingState.stashId, stakingState.controllerId) }
    )

    override suspend fun getControllerAccountInfo(stakingState: StakingState.Stash): AccountInfo {
        return localStorage.query(
            keyBuilder = { it.metadata.system().storage("Account").storageKey(it, stakingState.stashId) },
            binding = { scale, runtime -> scale?.let { bindAccountInfo(it, runtime) } ?: AccountInfo.empty() }
        )
    }

    override suspend fun minimumNominatorBond(): BigInteger {
        val minBond = queryStorageIfExists(
            storageName = "MinNominatorBond",
            binder = ::bindMinBond
        ) ?: BigInteger.ZERO

        val existentialDeposit = walletConstants.existentialDeposit()

        return minBond.max(existentialDeposit)
    }

    override suspend fun maxNominators(): BigInteger? = queryStorageIfExists(
        storageName = "MaxNominatorsCount",
        binder = ::bindMaxNominators
    )

    override suspend fun nominatorsCount(): BigInteger? = queryStorageIfExists(
        storageName = "CounterForNominators",
        binder = ::bindNominatorsCount
    )

    private suspend fun <T> queryStorageIfExists(
        storageName: String,
        binder: NonNullBinderWithType<T>
    ): T? {
        val runtime = getRuntime()

        return runtime.metadata.staking().storageOrNull(storageName)?.let { storageEntry ->
            localStorage.query(
                keyBuilder = { storageEntry.storageKey() },
                binding = { scale, _ -> scale?.let { binder(scale, runtime, storageEntry.returnType()) } }
            )
        }
    }

    override fun stakingStoriesFlow(): Flow<List<StakingStory>> {
        return stakingStoriesDataSource.getStoriesFlow()
    }

    override suspend fun ledgerFlow(stakingState: StakingState.Stash): Flow<StakingLedger> {
        return localStorage.observe(
            networkType = stakingState.controllerAddress.networkType(),
            keyBuilder = { it.metadata.staking().storage("Ledger").storageKey(it, stakingState.controllerId) },
            binder = { scale, runtime -> scale?.let { bindStakingLedger(it, runtime) } }
        ).filterNotNull()
    }

    override suspend fun ledger(address: String) = remoteStorage.query(
        keyBuilder = { it.metadata.staking().storage("Ledger").storageKey(it, address.toAccountId()) },
        binding = { scale, runtime -> scale?.let { bindStakingLedger(it, runtime) } }
    )

    private fun createExposuresFlow(): Flow<Map<String, Exposure>> {
        val exposuresFlow = MutableSharedFlow<AccountIdMap<Exposure>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        accountRepository.selectedNetworkTypeFlow()
            .onEach { exposuresFlow.resetReplayCache() } // invalidating cache on network change
            .flatMapLatest { networkType ->
                runtimeProperty.observe()
                    .filter { it.metadata.hasModule(Modules.STAKING) } // check that staking is supported
                    .flatMapLatest { runtime ->
                        storageCache.observeActiveEraIndex(runtime, networkType)
                    }
            }
            .onEach { exposuresFlow.resetReplayCache() } // invalidating cache on era change
            .mapLatest(::getElectedValidatorsExposure)
            .onEach(exposuresFlow::emit)
            .inBackground()
            .launchIn(GlobalScope)

        return exposuresFlow
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
        return localStorage.observe(
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
        return localStorage.observe(
            networkType = networkType,
            keyBuilder = { it.metadata.staking().storage("Nominators").storageKey(it, stashId) },
            binder = { scale, runtime -> scale?.let { bindNominations(it, runtime) } }
        )
    }

    private fun isSlashed(
        slashingSpans: SlashingSpans?,
        activeEraIndex: BigInteger,
        slashDeferDuration: BigInteger,
    ) = slashingSpans != null && activeEraIndex - slashingSpans.lastNonZeroSlash < slashDeferDuration

    private suspend fun getRuntime() = runtimeProperty.get()
}

package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.impl.domain.WalletSyncService
import jp.co.soramitsu.account.impl.domain.LegacyNetworkAccountUpgrade
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.appConfig
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.model.AppConfig
import jp.co.soramitsu.common.domain.model.toDomain
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.RemoteAssetsInitializer
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.wallet.impl.data.repository.PricesSyncService
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import kotlin.coroutines.CoroutineContext
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class AppInitializer @OptIn(ExperimentalCoroutinesApi::class) constructor(
    private val chainRegistry: ChainRegistry,
    private val chainSyncService: ChainSyncService,
    private val runtimeSyncService: RuntimeSyncService,
    private val pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario,
    private val walletSyncService: WalletSyncService,
    private val balancesUpdateSystem: UpdateSystem,
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val remoteAssetsInitializer: RemoteAssetsInitializer,
    private val preferences: Preferences,
    private val getAvailableFiatCurrencies: GetAvailableFiatCurrencies,
    private val pricesSyncService: PricesSyncService,
    private val productionAssetDiscoverySweep: AssetDiscoveryService,
    private val chainsRepository: ChainsRepository,
    private val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob(),
    private val legacyNetworkAccountUpgrade: LegacyNetworkAccountUpgrade? = null
) {

    data class Step(val type: InitializationStep, val action: suspend () -> Unit)

    private val scope = CoroutineScope(coroutineContext)
    private var dailyAssetSweepJob: Job? = null
    private var assetSweepTriggersJob: Job? = null
    private val assetSweepWakeups = Channel<Unit>(capacity = Channel.CONFLATED)

    suspend fun invoke(startFrom: InitializationStep = InitializationStep.All): InitializeResult =
        withContext(coroutineContext) {
            val restartableSteps = listOf(
                // 1. Get remote app config to indicate current version of the app is supported or not.
                // On fail we can't use the app, but we can reload.
                Step(InitializationStep.AppConfig, ::getRemoteConfig),
                // 2. Load chains.json config. If it fails - we don't have chains
                // need retry
                Step(InitializationStep.ChainsConfig, chainSyncService::syncUp),
                // 3. Load substrate chains types. If it fails - we still can use
                // the app but without substrate chains. Needs retry if we use this ecosystem.
                Step(InitializationStep.SubstrateTypes, runtimeSyncService::syncTypes),
            )

            val stepsToExecute = if (startFrom == InitializationStep.All) {
                restartableSteps
            } else {
                restartableSteps.dropWhile { it.type != startFrom }
            }

            val catalogBeforeChainsRefresh = if (
                stepsToExecute.any { it.type == InitializationStep.ChainsConfig }
            ) {
                runCatching { chainsRepository.getChains() }.getOrDefault(emptyList())
            } else {
                null
            }

            for (step in stepsToExecute) {
                try {
                    step.action()
                } catch (e: Throwable) {
                    return@withContext InitializeResult.ErrorCanRetry(e, step.type)
                }
            }

            catalogBeforeChainsRefresh?.let { before ->
                val after = runCatching { chainsRepository.getChains() }.getOrDefault(emptyList())
                if (catalogChanged(before, after)) {
                    assetSweepWakeups.trySend(Unit)
                }
            }

            // Load all remote assets. Auto retry 3 times
            launch {
                try {
                    val before = runCatching { chainsRepository.getChains() }.getOrDefault(emptyList())
                    remoteAssetsInitializer.invoke()
                    val after = runCatching { chainsRepository.getChains() }.getOrDefault(emptyList())
                    if (catalogChanged(before, after)) {
                        assetSweepWakeups.trySend(Unit)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }


            // Build all runtimes, connections for chains. There is no control over it here, so just run
            chainRegistry.syncUp()

            // Optional account expansion runs outside the awaited startup
            // transaction. Existing wallets remain usable while it retries.
            scope.launch { legacyNetworkAccountUpgrade?.upgrade() }

            // Start sync of the wallets - load balances, build assets. Can't control it here - just run
            walletSyncService.start()

            // Start balances updates (subscriptions or manual). Just run
            runBalancesUpdate()
                .launchIn(scope)
            startAssetDiscoveryTriggers()
            startDailyProductionAssetSweep()


            // other initializations

            coroutineScope {
                // Fetch feature toggle for pendulum pre-installed wallets
                // if it fails, we continue initialization
                pendulumPreInstalledAccountsScenario.fetchFeatureToggle()
                // Load and save to DB scam addresses
                try {
                    walletRepository.updatePhishingAddresses()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }

                // sync prices

                try {
                    getAvailableFiatCurrencies.sync()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }

                try {
                    pricesSyncService.sync()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            return@withContext InitializeResult.Success
        }

    suspend fun getRemoteConfig(): AppConfig {
        val remoteVersion = kotlin.runCatching { walletRepository.getRemoteConfig() }

        val config = if (remoteVersion.isSuccess) {
            preferences.appConfig = remoteVersion.requireValue()
            remoteVersion.requireValue()
        } else {
            val localVersion = preferences.appConfig
            localVersion
        }

        val domainConfig = config.toDomain()
        if (domainConfig.isCurrentVersionSupported.not()) {
            throw NotSupportedAppVersionException()
        }
        return domainConfig
    }

    private suspend fun runBalancesUpdate(): Flow<Updater.SideEffect> =
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(2.toDuration(DurationUnit.MINUTES)) {
                accountRepository.allMetaAccountsFlow()
                    .filter { accounts -> accounts.all { it.initialized } }
                    .filter { it.isNotEmpty() }
                    .first()
            }
            return@withContext balancesUpdateSystem.start().inBackground()
        }

    private fun startDailyProductionAssetSweep() {
        if (dailyAssetSweepJob?.isActive == true) return
        dailyAssetSweepJob = scope.launch {
            while (isActive) {
                if (assetSweepWakeups.tryReceive().isSuccess) {
                    productionAssetDiscoverySweep.resetForFullSweep()
                }

                if (!productionAssetDiscoverySweep.isDue()) {
                    awaitAssetSweepWakeup(
                        productionAssetDiscoverySweep.millisUntilDue()
                            .coerceAtMost(MAX_SCHEDULER_SLEEP_MILLIS)
                    )
                    continue
                }

                val result = productionAssetDiscoverySweep.scanNextBatch()
                if (result.hasMore) {
                    awaitAssetSweepWakeup(ProductionAssetDiscoverySweep.CONTINUATION_DELAY_MILLIS)
                }
            }
        }
    }

    private fun startAssetDiscoveryTriggers() {
        if (assetSweepTriggersJob?.isActive == true) return
        assetSweepTriggersJob = scope.launch {
            launch {
                accountRepository.allMetaAccountsFlow()
                    .map { accounts ->
                        accounts.filter { it.initialized }
                            .map { account ->
                                AccountDiscoveryFingerprint(
                                    id = account.id,
                                    substrateKeyHash = account.substratePublicKey?.contentHashCode(),
                                    substrateAccountHash = account.substrateAccountId?.contentHashCode(),
                                    ethereumKeyHash = account.ethereumPublicKey?.contentHashCode(),
                                    ethereumAddressHash = account.ethereumAddress?.contentHashCode(),
                                    tonKeyHash = account.tonPublicKey?.contentHashCode(),
                                    chainAccounts = account.chainAccounts
                                        .map { (chainId, chainAccount) ->
                                            ChainAccountDiscoveryFingerprint(
                                                chainId = chainId,
                                                publicKeyHash = chainAccount.publicKey.contentHashCode(),
                                                accountIdHash = chainAccount.accountId.contentHashCode()
                                            )
                                        }
                                        .sortedBy(ChainAccountDiscoveryFingerprint::chainId)
                                )
                            }
                            .sortedBy(AccountDiscoveryFingerprint::id)
                    }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { assetSweepWakeups.trySend(Unit) }
            }
            launch {
                // A null trigger is the explicit all-network pull-to-refresh signal. Network
                // selection emits a chain id and remains display-only for discovery purposes.
                BalanceUpdateTrigger.observe()
                    .filter { it == null }
                    .collect { assetSweepWakeups.trySend(Unit) }
            }
            launch {
                // Observe the complete persisted registry catalog. Rank, selected node and the
                // set of currently connected chains are intentionally excluded: they are
                // presentation/connection state and must not control background discovery.
                chainsRepository.chainsFlow()
                    .map(::canonicalCatalogFingerprint)
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { assetSweepWakeups.trySend(Unit) }
            }
        }
    }

    private suspend fun awaitAssetSweepWakeup(timeoutMillis: Long) {
        val woke = withTimeoutOrNull(timeoutMillis.coerceAtLeast(1L)) {
            assetSweepWakeups.receive()
            true
        } == true
        if (woke) {
            productionAssetDiscoverySweep.resetForFullSweep()
        }
    }

    private data class AccountDiscoveryFingerprint(
        val id: Long,
        val substrateKeyHash: Int?,
        val substrateAccountHash: Int?,
        val ethereumKeyHash: Int?,
        val ethereumAddressHash: Int?,
        val tonKeyHash: Int?,
        val chainAccounts: List<ChainAccountDiscoveryFingerprint>
    )

    private data class ChainAccountDiscoveryFingerprint(
        val chainId: String,
        val publicKeyHash: Int,
        val accountIdHash: Int
    )

    internal companion object {
        fun canonicalCatalogFingerprint(chains: List<Chain>): List<ChainDiscoveryFingerprint> =
            chains.map { chain ->
                ChainDiscoveryFingerprint(
                    chainId = chain.id,
                    isTestNet = chain.isTestNet,
                    ecosystem = chain.ecosystem.name,
                    remoteAssetsSource = chain.remoteAssetsSource?.name,
                    defaultNodeUrls = chain.nodes.asSequence()
                        .filter { it.isDefault }
                        .map { it.url }
                        .sorted()
                        .toList(),
                    assets = chain.assets.map { asset ->
                        AssetDiscoveryFingerprint(
                            assetId = asset.id,
                            currencyId = asset.currencyId,
                            precision = asset.precision,
                            priceId = asset.priceId,
                            isNative = asset.isNative
                        )
                    }.sortedBy(AssetDiscoveryFingerprint::assetId)
                )
            }.sortedBy(ChainDiscoveryFingerprint::chainId)

        fun catalogChanged(before: List<Chain>, after: List<Chain>): Boolean =
            canonicalCatalogFingerprint(before) != canonicalCatalogFingerprint(after)

        private const val MAX_SCHEDULER_SLEEP_MILLIS = 60L * 60L * 1_000L
    }

    internal data class ChainDiscoveryFingerprint(
        val chainId: String,
        val isTestNet: Boolean,
        val ecosystem: String,
        val remoteAssetsSource: String?,
        val defaultNodeUrls: List<String>,
        val assets: List<AssetDiscoveryFingerprint>
    )

    internal data class AssetDiscoveryFingerprint(
        val assetId: String,
        val currencyId: String?,
        val precision: Int,
        val priceId: String?,
        val isNative: Boolean?
    )
}

sealed interface InitializeResult {
    data object Success : InitializeResult
    data class ErrorCanRetry(val error: Throwable, val step: InitializationStep) : InitializeResult
}

enum class InitializationStep {
    All, AppConfig, ChainsConfig, SubstrateTypes,
}

class NotSupportedAppVersionException : RuntimeException()

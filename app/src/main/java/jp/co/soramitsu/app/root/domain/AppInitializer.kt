package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.impl.domain.WalletSyncService
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
import jp.co.soramitsu.runtime.multiNetwork.chain.RemoteAssetsInitializer
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.wallet.impl.data.repository.PricesSyncService
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class AppInitializer(
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
    private val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob()
) {

    data class Step(val type: InitializationStep, val action: suspend () -> Unit)

    private val scope = CoroutineScope(coroutineContext)

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

            for (step in stepsToExecute) {
                try {
                    step.action()
                } catch (e: Throwable) {
                    return@withContext InitializeResult.ErrorCanRetry(e, step.type)
                }
            }

            // Load all remote assets. Auto retry 3 times
            try {
                launch {
                    remoteAssetsInitializer.invoke()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }


            // Build all runtimes, connections for chains. There is no control over it here, so just run
            chainRegistry.syncUp()

            // Start sync of the wallets - load balances, build assets. Can't control it here - just run
            walletSyncService.start()

            // Start balances updates (subscriptions or manual). Just run
            runBalancesUpdate()
                .launchIn(scope)


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
}

sealed interface InitializeResult {
    data object Success : InitializeResult
    data class ErrorCanRetry(val error: Throwable, val step: InitializationStep) : InitializeResult
}

enum class InitializationStep {
    All, AppConfig, ChainsConfig, SubstrateTypes,
}

class NotSupportedAppVersionException : RuntimeException()

package jp.co.soramitsu.account.impl.domain

import android.util.Log
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.hasEthereum
import jp.co.soramitsu.account.api.domain.model.hasSubstrate
import jp.co.soramitsu.account.api.domain.model.hasTon
import jp.co.soramitsu.account.impl.data.mappers.mapMetaAccountLocalToMetaAccount
import jp.co.soramitsu.account.impl.data.mappers.toLocal
import jp.co.soramitsu.common.data.network.nomis.NomisApi
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.positiveOrNull
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.NomisScoresDao
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.NomisWalletScoreLocal
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.BSCChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ethereumChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polygonChainId
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val TAG = "WalletSyncService"

class WalletSyncService(
    private val metaAccountDao: MetaAccountDao,
    private val chainsRepository: ChainsRepository,
    private val chainRegistry: ChainRegistry,
    private val remoteStorageSource: RemoteStorageSource,
    private val assetDao: AssetDao,
    private val nomisApi: NomisApi,
    private val nomisScoresDao: NomisScoresDao,
    private val balanceLoaderProvider: BalanceLoader.Provider,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val scope =
        CoroutineScope(dispatcher + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.d(
                TAG,
                "WalletSyncService scope error: $throwable"
            )
        })

    private val nomisUpdateScope =
        CoroutineScope(dispatcher + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.d(
                TAG,
                "Nomis scope error: $throwable"
            )
        })

    private var syncJob: Job? = null

    fun start() {
        observeNotInitializedMetaAccounts1()
//        observeNotInitializedChainAccounts()
        observeNomisScores()
    }

    private fun observeNotInitializedMetaAccounts() {
        metaAccountDao.observeNotInitializedMetaAccounts().filter { it.isNotEmpty() }
            .onEach { localMetaAccounts ->
                syncJob?.cancel()
                syncJob = scope.launch {
                    val chains = chainsRepository.getChains()

                    val metaAccounts = localMetaAccounts.map { accountInfo ->
                        mapMetaAccountLocalToMetaAccount(
                            chains.associateBy { it.id },
                            accountInfo
                        )
                    }.toSet()

                    val accountHasAssetWithPositiveBalanceMap = mutableMapOf<Long, Boolean>()
                    val syncedChains = mutableSetOf<ChainId>()
                    supervisorScope {
                        val chainsBalancesDeferred = chainsRepository.getChains().map { chain ->
                            val filteredMetaAccounts = when(chain.ecosystem) {
                                Ecosystem.Substrate,
                                Ecosystem.Ethereum,
                                Ecosystem.EthereumBased -> metaAccounts.asSequence().filter { it.substratePublicKey != null || it.ethereumPublicKey != null }
                                Ecosystem.Ton -> metaAccounts.asSequence().filter { it.tonPublicKey != null }
                            }.toSet()
                            val chainSyncDeferred = async {
                                val provider = balanceLoaderProvider.invoke(chain)
                                val balances = withTimeoutOrNull(15_000) {
                                    provider.loadBalance(filteredMetaAccounts)
                                } ?: let {
                                    filteredMetaAccounts.map { meta ->
                                        chainsRepository.getChain(chain.id).assets.mapNotNull { asset ->
                                            val accountId =
                                                meta.accountId(chain) ?: return@mapNotNull null

                                            AssetBalanceUpdateItem(
                                                metaId = meta.id,
                                                chainId = chain.id,
                                                accountId = accountId,
                                                id = asset.id
                                            )
                                        }
                                    }.flatten()

                                }
                                val balancesByMetaIds = balances.groupBy { it.metaId }

                                metaAccounts.forEach { metaAccount ->
                                    val accountBalances =
                                        balancesByMetaIds[metaAccount.id] ?: emptyList()
                                    if (accountBalances.any { it.freeInPlanks.positiveOrNull() != null }) {
                                        accountHasAssetWithPositiveBalanceMap[metaAccount.id] = true
                                    }
                                }

                                balances
                            }

                            chainSyncDeferred.invokeOnCompletion {
                                syncedChains.add(chain.id)
                            }
                            chainSyncDeferred
                        }

                        val allBalances = chainsBalancesDeferred.awaitAll().flatten()

                        val assetsLocal = allBalances.mapNotNull { balance ->
                            val chain =
                                chainsRepository.getChains().find { it.id == balance.chainId } ?: return@mapNotNull null
                            val chainAsset = chain.assetsById.getOrDefault(balance.id, null)
                                ?: return@mapNotNull null

                            val isPopularUtilityAsset =
                                chain.rank != null && chainAsset.isUtility

                            val accountHasAssetWithPositiveBalance =
                                accountHasAssetWithPositiveBalanceMap[balance.metaId] == true

                            val isTonAsset = chain.ecosystem == Ecosystem.Ton && chainAsset.symbol.equals("TON", ignoreCase = true)

                            AssetLocal(
                                id = balance.id,
                                chainId = balance.chainId,
                                accountId = balance.accountId,
                                metaId = balance.metaId,
                                tokenPriceId = chainAsset.priceId,
                                freeInPlanks = balance.freeInPlanks,
                                reservedInPlanks = balance.reservedInPlanks,
                                miscFrozenInPlanks = balance.miscFrozenInPlanks,
                                feeFrozenInPlanks = balance.feeFrozenInPlanks,
                                enabled = balance.freeInPlanks.positiveOrNull() != null || (!accountHasAssetWithPositiveBalance && isPopularUtilityAsset) || isTonAsset
                            )
                        }
                        assetsLocal.groupBy { it.metaId }.forEach { b ->
                            runCatching { assetDao.insertAssets(b.value) }
                                .onFailure { Log.d(TAG, "failed to insert ${b.value.size} assets for metaId: ${b.key}, reason: $it") }
                                .onSuccess { Log.d(TAG, "successfully inserted ${b.value.size} assets for metaId: ${b.key} ") }
                        }

                        coroutineContext.job.invokeOnCompletion {
                            val errorMessage = it?.let { "with error: ${it.message}" } ?: ""
                            Log.d(TAG, "balances sync completed $errorMessage")
                        }
                    }
                    coroutineScope {
                        metaAccountDao.markAccountsInitialized(metaAccounts.map { it.id })
                        hideEmptyAssetsIfThereAreAtLeastOnePositiveBalanceByMetaAccounts(
                            metaAccounts.toList()
                        )
                    }
                }
            }
            .launchIn(scope)
    }

    private fun observeNotInitializedMetaAccounts1() {
        metaAccountDao.observeNotInitializedMetaAccounts().filter { it.isNotEmpty() }
            .onEach { localMetaAccounts ->
                handleNewAccounts(localMetaAccounts)
            }
            .launchIn(scope)
    }

    private val processingAccounts = mutableSetOf<Long>()
    private val mutex = Mutex()

    private suspend fun handleNewAccounts(accounts: List<RelationJoinedMetaAccountInfo>) {
        accounts.forEach { account ->
            mutex.withLock {
                if (processingAccounts.contains(account.metaAccount.id)) return@forEach
                processingAccounts.add(account.metaAccount.id)
            }
            launchAssetLoading(account)
        }
    }

    private fun launchAssetLoading(account: RelationJoinedMetaAccountInfo) {
        scope.launch {
            try {
                val freshAccount = metaAccountDao.getMetaAccount(account.metaAccount.id) ?: return@launch
                if (freshAccount.initialized) return@launch

                loadBalances(account)

                metaAccountDao.markAccountsInitialized(listOf(account.metaAccount.id))

            } catch (e: Exception) {
                Log.d(TAG, "Error while loading balance: $e")
            } finally {
                mutex.withLock {
                    processingAccounts.remove(account.metaAccount.id)
                }
            }
        }
    }
    private suspend fun loadBalances(account: RelationJoinedMetaAccountInfo) {
        val chains = chainsRepository.getChains()

        val metaAccount =
            mapMetaAccountLocalToMetaAccount(
                chains.associateBy { it.id },
                account
            )

        val ecosystemsToLoad = mutableSetOf<Ecosystem>().apply {
            if(metaAccount.hasEthereum) {
                add(Ecosystem.Ethereum)
                add(Ecosystem.EthereumBased)
            }
            if(metaAccount.hasSubstrate) {
                add(Ecosystem.Substrate)
            }
            if(metaAccount.hasTon) {
                add(Ecosystem.Ton)
            }
        }
        val chainsToLoad = chains.asSequence().filter { it.ecosystem in ecosystemsToLoad }.toList()

        val balances = coroutineScope {
            chainsToLoad
                .map { async { loadBalances(metaAccount, it) } }
                .awaitAll()
                .flatten()
        }

        val assetsLocal = convertBalancesToAssetLocal(balances, chainsToLoad)
        assetDao.insertAssets(assetsLocal)
        assetDao.hideEmptyAssetsIfThereAreAtLeastOnePositiveBalance(metaAccount.id)
    }

    private suspend fun loadBalances(metaAccount: MetaAccount, chain: Chain): List<AssetBalanceUpdateItem> {
        val provider = balanceLoaderProvider.invoke(chain)
        val remoteBalances =  retry(retries = 3, delay = 1.seconds, factor = 2.0) {
                provider.loadBalance(setOf(metaAccount))
            }
        val balances = remoteBalances ?: chain.assets.mapNotNull { asset ->
            val accountId =
                metaAccount.accountId(chain) ?: return@mapNotNull null

            AssetBalanceUpdateItem(
                metaId = metaAccount.id,
                chainId = chain.id,
                accountId = accountId,
                id = asset.id
            )
        }
        return balances
    }

    private fun convertBalancesToAssetLocal(balances: List<AssetBalanceUpdateItem>, chains: List<Chain>): List<AssetLocal> {
        val accountHasAssetWithPositiveBalance = balances.any { it.freeInPlanks.positiveOrNull() != null }
        return balances.mapNotNull { balance ->
            val chain = chains.find { it.id == balance.chainId } ?: return@mapNotNull null
            val chainAsset = chain.assetsById.getOrDefault(balance.id, null) ?: return@mapNotNull null
            val isPopularUtilityAsset =
                chain.rank != null && chainAsset.isUtility
            val isTonAsset = chain.ecosystem == Ecosystem.Ton && chainAsset.symbol.equals("TON", ignoreCase = true)

            AssetLocal(
                id = balance.id,
                chainId = balance.chainId,
                accountId = balance.accountId,
                metaId = balance.metaId,
                tokenPriceId = chainAsset.priceId,
                freeInPlanks = balance.freeInPlanks,
                reservedInPlanks = balance.reservedInPlanks,
                miscFrozenInPlanks = balance.miscFrozenInPlanks,
                feeFrozenInPlanks = balance.feeFrozenInPlanks,
                enabled = balance.freeInPlanks.positiveOrNull() != null || (!accountHasAssetWithPositiveBalance && isPopularUtilityAsset) || isTonAsset
            )
        }
    }

    private suspend fun <T> retry(
        retries: Int,
        delay: Duration,
        factor: Double,
        block: suspend () -> T
    ): T? {
        var currentDelay = delay
        repeat(retries) {
            try {
                return block()
            } catch (e: Exception) {
                delay(currentDelay)
                currentDelay = (currentDelay * factor)
            }
        }
        return null
    }

//    private fun observeNotInitializedChainAccounts() {
//        metaAccountDao.observeNotInitializedChainAccounts().filter { it.isNotEmpty() }
//            .onEach { chainAccounts ->
//                chainRegistry.configsSyncDeferred.joinAll()
//                val chains =
//                    chainAccounts.map { chainRegistry.getChain(it.chainId) }.associateBy { it.id }
//                val ethereumChains =
//                    chains.values.filter { it.isEthereumChain }.sortedByDescending { it.rank }
//                val substrateChains =
//                    chains.values.filter { !it.isEthereumChain }.sortedByDescending { it.rank }
//
//                supervisorScope {
//                    launch {
//                        ethereumChains.forEach { chain ->
//                            launch {
//                                val assetsDeferred = async {
//                                    if (chainRegistry.checkChainSyncedUp(chain).not()) {
//                                        chainRegistry.setupChain(chain)
//                                    }
//                                    val connection =
//                                        withTimeoutOrNull(CHAIN_SYNC_TIMEOUT_MILLIS) {
//                                            val connection =
//                                                chainRegistry.awaitEthereumConnection(chain.id)
//                                            // await connecting to the node
//                                            connection.statusFlow.first { it is EvmConnectionStatus.Connected }
//                                            connection
//                                        }
//
//                                    chainAccounts.map { chainAccount ->
//                                        val accountId = chainAccount.accountId
//
//                                        val accountBalances = chain.assets.map { chainAsset ->
//                                            async {
//                                                val balance = kotlin.runCatching {
//                                                    connection?.web3j?.fetchEthBalance(
//                                                        chainAsset,
//                                                        accountId.toHexString(true)
//                                                    )
//                                                }.getOrNull()
//
//                                                AssetLocal(
//                                                    id = chainAsset.id,
//                                                    chainId = chain.id,
//                                                    accountId = accountId,
//                                                    metaId = chainAccount.metaId,
//                                                    tokenPriceId = chainAsset.priceId,
//                                                    freeInPlanks = balance,
//                                                    reservedInPlanks = BigInteger.ZERO,
//                                                    miscFrozenInPlanks = BigInteger.ZERO,
//                                                    feeFrozenInPlanks = BigInteger.ZERO,
//                                                    bondedInPlanks = BigInteger.ZERO,
//                                                    redeemableInPlanks = BigInteger.ZERO,
//                                                    unbondingInPlanks = BigInteger.ZERO,
//                                                    enabled = balance.positiveOrNull() != null || chainAsset.isUtility
//                                                )
//                                            }
//                                        }
//                                        accountBalances.awaitAll()
//                                    }.flatten()
//                                }
//                                val localAssets = assetsDeferred.await()
//                                assetDao.insertAssets(localAssets)
//                            }
//                        }
//                    }
//                    launch {
//                        substrateChains.onEach { chain ->
//                            launch {
//                                val assetsDeferred = async {
//                                    val emptyAssets: MutableList<AssetLocal> = mutableListOf()
//                                    val runtime = withTimeoutOrNull(CHAIN_SYNC_TIMEOUT_MILLIS) {
//                                        if (chainRegistry.checkChainSyncedUp(chain).not()) {
//                                            chainRegistry.setupChain(chain)
//                                        }
//
//                                        // awaiting runtime snapshot
//                                        chainRegistry.awaitRuntimeProvider(chain.id).get()
//                                    }
//
//                                    val isEquilibriumTypeChain =
//                                        chain.utilityAsset != null && chain.utilityAsset!!.typeExtra == ChainAssetType.Equilibrium
//
//                                    if (isEquilibriumTypeChain) {
//                                        buildEquilibriumAssets(
//                                            chainAccounts.map { it.metaId to it.accountId },
//                                            chain,
//                                            runtime
//                                        )
//                                    } else {
//                                        val allAccountsStorageKeys =
//                                            chainAccounts.map { chainAccount ->
//                                                buildSubstrateStorageKeys(
//                                                    chain,
//                                                    runtime,
//                                                    chainAccount.metaId,
//                                                    chainAccount.accountId
//                                                )
//                                            }.flatten()
//
//                                        val keysToQuery =
//                                            allAccountsStorageKeys.mapNotNull { metadata ->
//                                                // if storage key build is failed - we put the empty assets
//                                                if (metadata.key == null) {
//                                                    emptyAssets.add(
//                                                        AssetLocal(
//                                                            accountId = metadata.accountId,
//                                                            id = metadata.asset.id,
//                                                            chainId = metadata.asset.chainId,
//                                                            metaId = metadata.metaAccountId,
//                                                            tokenPriceId = metadata.asset.priceId,
//                                                            enabled = false,
//                                                            freeInPlanks = BigInteger.valueOf(-1)
//                                                        )
//                                                    )
//                                                }
//                                                metadata.key
//                                            }.toList()
//
//                                        val storageKeyToResult = remoteStorageSource.queryKeys(
//                                            keysToQuery,
//                                            chain.id,
//                                            null
//                                        )
//
//                                        allAccountsStorageKeys.map { metadata ->
//                                            val hexRaw =
//                                                storageKeyToResult.getOrDefault(
//                                                    metadata.key,
//                                                    null
//                                                )
//
//                                            val assetBalance =
//                                                runtime?.let {
//                                                    handleBalanceResponse(
//                                                        it,
//                                                        metadata.asset,
//                                                        hexRaw
//                                                    ).getOrNull().toAssetBalance()
//                                                } ?: AssetBalance()
//
//                                            AssetLocal(
//                                                id = metadata.asset.id,
//                                                chainId = chain.id,
//                                                accountId = metadata.accountId,
//                                                metaId = metadata.metaAccountId,
//                                                tokenPriceId = metadata.asset.priceId,
//                                                freeInPlanks = assetBalance.freeInPlanks,
//                                                reservedInPlanks = assetBalance.reservedInPlanks,
//                                                miscFrozenInPlanks = assetBalance.miscFrozenInPlanks,
//                                                feeFrozenInPlanks = assetBalance.feeFrozenInPlanks,
//                                                bondedInPlanks = assetBalance.bondedInPlanks,
//                                                redeemableInPlanks = assetBalance.redeemableInPlanks,
//                                                unbondingInPlanks = assetBalance.unbondingInPlanks,
//                                                enabled = assetBalance.freeInPlanks.positiveOrNull() != null || metadata.asset.isUtility
//                                            )
//                                        } + emptyAssets
//                                    }
//                                }
//                                val localAssets = assetsDeferred.await()
//                                assetDao.insertAssets(localAssets)
//                            }
//                        }
//                    }
//
//                    this
//                }.coroutineContext.job.join()
//                coroutineScope {
//                    chainAccounts.forEach {
//                        metaAccountDao.markChainAccountInitialized(it.metaId, it.chainId)
//                    }
//                }
//            }
//            .launchIn(scope)
//    }

    private suspend fun hideEmptyAssetsIfThereAreAtLeastOnePositiveBalanceByMetaAccounts(
        metaAccounts: List<MetaAccount>
    ) {
        hideEmptyAssetsIfThereAreAtLeastOnePositiveBalanceByMetaIds(metaAccounts.map { it.id })
    }

    private suspend fun hideEmptyAssetsIfThereAreAtLeastOnePositiveBalanceByMetaIds(metaAccountsIds: List<Long>) {
        coroutineScope {
            metaAccountsIds.forEach {
                withContext(Dispatchers.IO) {
                    assetDao.hideEmptyAssetsIfThereAreAtLeastOnePositiveBalance(
                        it
                    )
                }
            }
        }
    }

    private fun observeNomisScores() {
        var syncJob: Job? = null
        val supportedChains = setOf(
            ethereumChainId,
            BSCChainId,
            polygonChainId,
        )

        metaAccountDao.observeJoinedMetaAccountsInfo()
            .map { list ->
                list.filter { info ->
                    info.metaAccount.ethereumPublicKey != null || info.chainAccounts.any { it.chainId in supportedChains }
                }
            }
            .distinctUntilChangedBy {
                it.size + it.map { info -> info.chainAccounts }.flatten().size
            }
            .map { metaAccountInfo ->
                val existingScores = nomisScoresDao.getScores()
                val currentTime = System.currentTimeMillis()
                val twelveHoursMillis = 12 * 60 * 60 * 1000L
                val existingScoresToUpdate =
                    existingScores.filter { currentTime - it.updated > twelveHoursMillis }
                        .map { it.metaId }

                val newAccounts =
                    metaAccountInfo.filter { it.metaAccount.id !in existingScores.map { score -> score.metaId } }

                val accountsToUpdate = metaAccountInfo.asSequence()
                    .filter { it.metaAccount.id in existingScoresToUpdate }

                (newAccounts + accountsToUpdate).toSet()
            }
            .onEach { metaAccounts ->
                syncJob?.cancel()
                syncJob = nomisUpdateScope.launch {
                    syncNomisScores(*metaAccounts.toTypedArray())
                }
            }
            .launchIn(nomisUpdateScope)
    }

    private suspend fun syncNomisScores(vararg metaAccount: RelationJoinedMetaAccountInfo) {
        return coroutineScope {
            val supportedChains = setOf(
                ethereumChainId,
                BSCChainId,
                polygonChainId,
            )
            metaAccount.onEach { accountInfo ->
                launch {
                    val id = accountInfo.metaAccount.id
                    nomisScoresDao.insert(NomisWalletScoreLocal.loading(id))
                    runCatching {
                        val address = accountInfo.metaAccount.ethereumAddress
                            ?: accountInfo.chainAccounts.firstOrNull { it.chainId in supportedChains }?.publicKey?.ethereumAddressFromPublicKey()
                        nomisApi.getNomisScore(address!!.toHexString(true))
                    }.onSuccess { response ->
                        nomisScoresDao.insert(response.toLocal(id))
                    }.onFailure {
                        nomisScoresDao.insert(NomisWalletScoreLocal.error(id))
                    }
                }
            }
        }
    }
}

package jp.co.soramitsu.account.impl.domain

import android.util.Log
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.impl.data.mappers.mapMetaAccountLocalToMetaAccount
import jp.co.soramitsu.common.data.network.runtime.binding.AssetBalance
import jp.co.soramitsu.common.data.network.runtime.binding.toAssetBalance
import jp.co.soramitsu.common.utils.isNotZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.EvmConnectionStatus
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.wallet.api.data.cache.bindEquilibriumAccountData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

class WalletSyncService(
    private val metaAccountDao: MetaAccountDao,
    private val chainsRepository: ChainsRepository,
    private val chainRegistry: ChainRegistry,
    private val remoteStorageSource: RemoteStorageSource,
    private val assetDao: AssetDao,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    companion object {
        private const val CHAIN_SYNC_TIMEOUT_MILLIS: Long = 15_000L
    }

    private val scope =
        CoroutineScope(dispatcher + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.d(
                "WalletSyncService",
                "WalletSyncService scope error: $throwable"
            )
        })

    private var syncJob: Job? = null

    fun start(){
        metaAccountDao.observeNotInitializedMetaAccounts().filter { it.isNotEmpty() }
            .onEach { localMetaAccounts ->
                syncJob?.cancel()
                syncJob = scope.launch {
                    chainRegistry.configsSyncDeferred.joinAll()

                    val chains = chainsRepository.getChains()
                    val ethereumChains = chains.filter { it.isEthereumChain }
                    val substrateChains = chains.filter { !it.isEthereumChain }

                    val metaAccounts =
                        localMetaAccounts.map { accountInfo ->
                            mapMetaAccountLocalToMetaAccount(
                                chains.associateBy { it.id },
                                accountInfo
                            )
                        }

                    supervisorScope {
                        launch {
                            ethereumChains.forEach { chain ->
                                launch {
                                    val assetsDeferred = async {
                                        if (chainRegistry.checkChainSyncedUp(chain).not()) {
                                            chainRegistry.setupChain(chain)
                                        }
                                        val connection =
                                            withTimeoutOrNull(CHAIN_SYNC_TIMEOUT_MILLIS) {
                                                val connection =
                                                    chainRegistry.awaitEthereumConnection(chain.id)
                                                // await connecting to the node
                                                connection.statusFlow.first { it is EvmConnectionStatus.Connected }
                                                connection
                                            }

                                        metaAccounts.mapNotNull { metaAccount ->
                                            val accountId =
                                                metaAccount.accountId(chain)
                                                    ?: return@mapNotNull null

                                            chain.assets.map { chainAsset ->
                                                val balance = kotlin.runCatching {
                                                    connection?.web3j?.fetchEthBalance(
                                                        chainAsset,
                                                        accountId.toHexString(true)
                                                    )
                                                }.getOrNull()

                                                AssetLocal(
                                                    id = chainAsset.id,
                                                    chainId = chain.id,
                                                    accountId = accountId,
                                                    metaId = metaAccount.id,
                                                    tokenPriceId = chainAsset.priceId,
                                                    freeInPlanks = balance,
                                                    reservedInPlanks = BigInteger.ZERO,
                                                    miscFrozenInPlanks = BigInteger.ZERO,
                                                    feeFrozenInPlanks = BigInteger.ZERO,
                                                    bondedInPlanks = BigInteger.ZERO,
                                                    redeemableInPlanks = BigInteger.ZERO,
                                                    unbondingInPlanks = BigInteger.ZERO,
                                                    enabled = balance.isNotZero() || chain.rank != null && chainAsset.isUtility
                                                )
                                            }
                                        }.flatten()
                                    }
                                    val localAssets = assetsDeferred.await()
                                    assetDao.insertAssets(localAssets)
                                }
                            }
                        }
                        launch {
                            substrateChains.onEach { chain ->
                                launch {
                                    val assetsDeferred = async {
                                        val emptyAssets: MutableList<AssetLocal> = mutableListOf()
                                        val runtime = withTimeoutOrNull(CHAIN_SYNC_TIMEOUT_MILLIS) {
                                            if (chainRegistry.checkChainSyncedUp(chain).not()) {
                                                chainRegistry.setupChain(chain)
                                            }

                                            // awaiting runtime snapshot
                                            chainRegistry.awaitRuntimeProvider(chain.id).get()
                                        }

                                        val isEquilibriumTypeChain =
                                            chain.utilityAsset != null && chain.utilityAsset!!.typeExtra == ChainAssetType.Equilibrium

                                        if (isEquilibriumTypeChain) {
                                            buildEquilibriumAssets(metaAccounts, chain, runtime)
                                        } else {
                                            val allAccountsStorageKeys =
                                                metaAccounts.mapNotNull { metaAccount ->
                                                    val accountId =
                                                        metaAccount.accountId(chain)
                                                            ?: return@mapNotNull null
                                                    buildSubstrateStorageKeys(
                                                        chain,
                                                        runtime,
                                                        metaAccount.id,
                                                        accountId
                                                    )
                                                }.flatten()

                                            val keysToQuery =
                                                allAccountsStorageKeys.mapNotNull { metadata ->
                                                    // if storage key build is failed - we put the empty assets
                                                    if (metadata.key == null) {
                                                        emptyAssets.add(
                                                            AssetLocal(
                                                                accountId = metadata.accountId,
                                                                id = metadata.asset.id,
                                                                chainId = metadata.asset.chainId,
                                                                metaId = metadata.metaAccountId,
                                                                tokenPriceId = metadata.asset.priceId,
                                                                enabled = false,
                                                                freeInPlanks = BigInteger.valueOf(-1)
                                                            )
                                                        )
                                                    }
                                                    metadata.key
                                                }.toList()

                                            val storageKeyToResult = remoteStorageSource.queryKeys(
                                                keysToQuery,
                                                chain.id,
                                                null
                                            )

                                            allAccountsStorageKeys.map { metadata ->
                                                val hexRaw =
                                                    storageKeyToResult.getOrDefault(
                                                        metadata.key,
                                                        null
                                                    )

                                                val assetBalance =
                                                    runtime?.let {
                                                        handleBalanceResponse(
                                                            it,
                                                            metadata.asset,
                                                            hexRaw
                                                        ).getOrNull().toAssetBalance()
                                                    } ?: AssetBalance()

                                                AssetLocal(
                                                    id = metadata.asset.id,
                                                    chainId = chain.id,
                                                    accountId = metadata.accountId,
                                                    metaId = metadata.metaAccountId,
                                                    tokenPriceId = metadata.asset.priceId,
                                                    freeInPlanks = assetBalance.freeInPlanks,
                                                    reservedInPlanks = assetBalance.reservedInPlanks,
                                                    miscFrozenInPlanks = assetBalance.miscFrozenInPlanks,
                                                    feeFrozenInPlanks = assetBalance.feeFrozenInPlanks,
                                                    bondedInPlanks = assetBalance.bondedInPlanks,
                                                    redeemableInPlanks = assetBalance.redeemableInPlanks,
                                                    unbondingInPlanks = assetBalance.unbondingInPlanks,
                                                    enabled = assetBalance.freeInPlanks.isNotZero() || chain.rank != null && metadata.asset.isUtility
                                                )
                                            } + emptyAssets
                                        }
                                    }
                                    val localAssets = assetsDeferred.await()
                                    assetDao.insertAssets(localAssets)
                                }
                            }
                        }

                        this
                    }.coroutineContext.job.join()
                    metaAccountDao.markAccountsInitialized(metaAccounts.map { it.id })
                }
            }.launchIn(scope)

    }

    private suspend fun buildEquilibriumAssets(
        metaAccounts: List<MetaAccount>,
        chain: Chain,
        runtime: RuntimeSnapshot?
    ): List<AssetLocal> {
        val emptyAssets: MutableList<AssetLocal> = mutableListOf()

        val allAccountsStorageKeys = metaAccounts.mapNotNull { metaAccount ->
            val accountId = metaAccount.accountId(chain) ?: return@mapNotNull null
            buildEquilibriumStorageKeys(chain, runtime, metaAccount.id, accountId)
        }.associateBy { it.key }

        val keysToQuery =
            allAccountsStorageKeys.mapNotNull { (storageKey, metadata) ->
                // if storage key build is failed - we put the empty assets
                if (storageKey == null) {
                    // filling all the equilibrium assets
                    val empty = chain.assets.map {
                        AssetLocal(
                            accountId = metadata.accountId,
                            id = it.id,
                            chainId = it.chainId,
                            metaId = metadata.metaAccountId,
                            tokenPriceId = it.priceId,
                            enabled = false,
                            freeInPlanks = BigInteger.valueOf(-1)
                        )
                    }
                    emptyAssets.addAll(empty)
                }
                storageKey
            }.toList()

        val storageKeyToResult = remoteStorageSource.queryKeys(keysToQuery, chain.id, null)

        return storageKeyToResult.mapNotNull { (storageKey, hexRaw) ->
            val metadata = allAccountsStorageKeys[storageKey] ?: return@mapNotNull null

            val balanceData = runtime?.let { bindEquilibriumAccountData(hexRaw, it) }
            val equilibriumAssetsBalanceMap = balanceData?.data?.balances.orEmpty()

            chain.assets.map { asset ->
                val balance =
                    asset.currencyId?.toBigInteger()?.let {
                        equilibriumAssetsBalanceMap.getOrDefault(it, null)
                            .orZero()
                    }.orZero()
                AssetLocal(
                    accountId = metadata.accountId,
                    id = asset.id,
                    chainId = asset.chainId,
                    metaId = metadata.metaAccountId,
                    tokenPriceId = asset.priceId,
                    enabled = false,
                    freeInPlanks = balance
                )
            }
        }.flatten() + emptyAssets
    }
}
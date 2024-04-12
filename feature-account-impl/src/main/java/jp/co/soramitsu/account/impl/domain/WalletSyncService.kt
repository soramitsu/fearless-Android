package jp.co.soramitsu.account.impl.domain

import android.util.Log
import java.math.BigInteger
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
import jp.co.soramitsu.runtime.multiNetwork.connection.EvmConnectionStatus
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.wallet.api.data.cache.bindEquilibriumAccountData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

class WalletSyncService(
    private val metaAccountDao: MetaAccountDao,
    private val chainsRepository: ChainsRepository,
    private val chainRegistry: ChainRegistry,
    private val remoteStorageSource: RemoteStorageSource,
    private val assetDao: AssetDao,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob() + CoroutineExceptionHandler { coroutineContext, throwable -> Log.d("&&&", "WalletSyncService scope error: $throwable") })

    init {
        metaAccountDao.observeNotInitializedMetaAccounts().onEach { Log.d("&&&", "start sync ${it.size} accounts: ${it.map { it.metaAccount.name }}") }.filter { it.isNotEmpty() }
            .onEach { localMetaAccounts ->
                chainRegistry.configsSyncDeferred.join()

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

                coroutineScope {

                    launch {
                        ethereumChains.forEach { chain ->
                            launch {
                                val assetsDeferred = async {
                                    if (chainRegistry.checkChainSyncedUp(chain).not()) {
                                        chainRegistry.setupChain(chain)
                                    }
                                    val connection = chainRegistry.awaitEthereumConnection(chain.id)
                                    // await connecting to the node
                                    connection.statusFlow.first { it is EvmConnectionStatus.Connected }
//                                    this@launch.cancel()
                                    metaAccounts.mapNotNull { metaAccount ->
                                        val accountId =
                                            metaAccount.accountId(chain) ?: return@mapNotNull null
                                        chain.assets.map { chainAsset ->
                                            val balance = kotlin.runCatching {
                                                connection.web3j!!.fetchEthBalance(
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
                                Log.d("&&&&", "chain ${chain.name} sync completed")
                            }
                        }
                    }.invokeOnCompletion { Log.d("&&&&", "EVM CHAINS sync completed") }
                    launch {
                        substrateChains.onEach { chain ->
                            launch {
                                val assetsDeferred = async {
                                    if (chainRegistry.checkChainSyncedUp(chain).not()) {
                                        chainRegistry.setupChain(chain)
                                    }
                                    val emptyAssets: MutableList<AssetLocal> = mutableListOf()
                                    // awaiting runtime snapshot
                                    val runtime = chainRegistry.awaitRuntimeProvider(chain.id).get()

                                    val allAccountsStorageKeys =
                                        metaAccounts.mapNotNull { metaAccount ->
                                            val accountId =
                                                metaAccount.accountId(chain)
                                                    ?: return@mapNotNull null
                                            buildStorageKeys(
                                                chain,
                                                runtime,
                                                metaAccount.id,
                                                accountId
                                            )
                                        }.flatten().associateBy { it.key }

                                    val keysToQuery =
                                        allAccountsStorageKeys.mapNotNull { (storageKey, metadata) ->
                                            // if storage key build is failed - we put the empty assets
                                            if (storageKey == null) {
                                                // filling all the equilibrium assets
                                                if (chain.utilityAsset != null && chain.utilityAsset?.typeExtra == ChainAssetType.Equilibrium) {
                                                    chain.assets.map {
                                                        emptyAssets.add(
                                                            AssetLocal.createEmpty(
                                                                accountId = metadata.accountId,
                                                                assetId = it.id,
                                                                chainId = it.chainId,
                                                                metaId = metadata.metaAccountId,
                                                                priceId = it.priceId,
                                                                enabled = false
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    emptyAssets.add(
                                                        AssetLocal.createEmpty(
                                                            accountId = metadata.accountId,
                                                            assetId = metadata.asset.id,
                                                            chainId = metadata.asset.chainId,
                                                            metaId = metadata.metaAccountId,
                                                            priceId = metadata.asset.priceId,
                                                            enabled = false
                                                        )
                                                    )
                                                }
                                            }
                                            storageKey
                                        }.toList()

                                    val storageKeyToResult = remoteStorageSource.queryKeys(
                                        keysToQuery,
                                        chain.id,
                                        null
                                    )

                                    val equilibriumBalanceMap =
                                        if (chain.utilityAsset != null && chain.utilityAsset!!.typeExtra == ChainAssetType.Equilibrium) {
                                            val result =
                                                storageKeyToResult.values.firstNotNullOfOrNull { it }
                                            val balanceData =
                                                bindEquilibriumAccountData(result, runtime)
                                            balanceData?.data?.balances.orEmpty()
                                        } else {
                                            null
                                        }

                                    allAccountsStorageKeys.map { (storageKey, metadata) ->
                                        val hexRaw =
                                            storageKeyToResult.getOrDefault(storageKey, null)

                                        val assetBalance = if (equilibriumBalanceMap != null) {
                                            val balance =
                                                metadata.asset.currencyId?.toBigInteger()?.let {
                                                    equilibriumBalanceMap.getOrDefault(it, null)
                                                        .orZero()
                                                }.orZero()
                                            AssetBalance(freeInPlanks = balance)
                                        } else {
                                            handleBalanceResponse(
                                                runtime,
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
                                val localAssets = assetsDeferred.await()
                                assetDao.insertAssets(localAssets)
                                Log.d("&&&&", "chain ${chain.name} sync completed")
                            }
                        }
                    }.invokeOnCompletion { Log.d("&&&&", "SUBSTRATE CHAINS sync completed") }
                    this
                }.coroutineContext.job.join()
                Log.d("&&&", "marking accounts initialized ${metaAccounts.map { it.name }}")
                metaAccountDao.markAccountsInitialized(metaAccounts.map { it.id })
            }.launchIn(scope)
    }
}
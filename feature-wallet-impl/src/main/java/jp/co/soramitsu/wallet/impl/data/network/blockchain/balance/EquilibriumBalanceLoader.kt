package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.annotation.SuppressLint
import android.util.Log
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.impl.domain.StorageKeyWithMetadata
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.network.subscriptionFlowCatching
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.api.data.cache.bindEquilibriumAccountData
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.SubscribeBalanceRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

class EquilibriumBalanceLoader(
    chain: Chain,
    private val chainRegistry: ChainRegistry,
    private val remoteStorageSource: RemoteStorageSource
) : BalanceLoader(chain) {

    companion object {
        private const val CHAIN_SYNC_TIMEOUT_MILLIS: Long = 15_000L
    }

    private val tag = "EquilibriumBalanceLoader (${chain.name})"

    override suspend fun loadBalance(metaAccounts: Set<MetaAccount>): List<AssetBalanceUpdateItem> {
        return supervisorScope {
            val runtime = withTimeoutOrNull(CHAIN_SYNC_TIMEOUT_MILLIS) {
                if (chainRegistry.checkChainSyncedUp(chain).not()) {
                    chainRegistry.setupChain(chain)
                }

                // awaiting runtime snapshot
                chainRegistry.awaitRuntimeProvider(chain.id).get()
            }

            buildEquilibriumAssets(
                metaAccounts.map { it.id to it.substrateAccountId },
                chain,
                runtime
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun subscribeBalance(metaAccount: MetaAccount): Flow<BalanceLoaderAction> {
        metaAccount.substrateAccountId ?: return emptyFlow()
        return flow { emit(chainRegistry.awaitRuntimeProvider(chain.id).get()) }
            .transformLatest { runtime ->
                val storageKey = buildEquilibriumStorageKeys(
                    chain,
                    runtime,
                    metaAccount.id,
                    metaAccount.substrateAccountId!!
                )
                val socketService =
                    runCatching { chainRegistry.awaitConnection(chain.id).socketService }
                        .onFailure {
                            logError("Error getting socket for chain ${chain.name}: $it")
                        }
                        .getOrNull()
                        ?: return@transformLatest

                val request =
                    SubscribeBalanceRequest(listOf(storageKey.key ?: return@transformLatest))

                socketService.subscriptionFlowCatching(request)
                    .collect { subscriptionChangeResult ->
                        subscriptionChangeResult.onFailure {
                            logError("Balance subscription failed for chain ${chain.name}: $it")
                        }
                        val subscriptionChange =
                            subscriptionChangeResult.getOrNull()
                                ?: return@collect

                        val storageChange = subscriptionChange.storageChange()
                        val storageKeyToHex = storageChange.changes.map { it[0]!! to it[1] }


                        val storageKeyToHexRaw =
                            storageKeyToHex.singleOrNull() ?: return@collect
                        val eqHexRaw = storageKeyToHexRaw.second
                        val balanceData = bindEquilibriumAccountData(eqHexRaw, runtime)
                        val balances = balanceData?.data?.balances.orEmpty()

                        chain.assets.forEach asset@{ asset ->
                            val balance = balances.getOrDefault(
                                asset.currencyId?.toBigInteger().orZero(), null
                            ).orZero()

                            emit(
                                BalanceLoaderAction.UpdateBalance(
                                    AssetBalanceUpdateItem(
                                        id = asset.id,
                                        chainId = chain.id,
                                        accountId = metaAccount.substrateAccountId!!,
                                        metaId = metaAccount.id,
                                        freeInPlanks = balance,
                                    )
                                )
                            )
                        }
                    }
            }
    }

    private suspend fun buildEquilibriumAssets(
        accountInfo: List<Pair<Long, ByteArray?>>,
        chain: Chain,
        runtime: RuntimeSnapshot?
    ): List<AssetBalanceUpdateItem> {
        // Without a runtime no storage key can be built and nothing was read. Emit no update then,
        // like the Substrate loader: the cache keeps the last known balances instead of "-1".
        runtime ?: return emptyList()

        val allAccountsStorageKeys = accountInfo.mapNotNull { (metaId, accountId) ->
            accountId ?: return@mapNotNull null
            buildEquilibriumStorageKeys(chain, runtime, metaId, accountId)
        }.associateBy { it.key }

        val keysToQuery = allAccountsStorageKeys.keys.filterNotNull()

        val storageKeyToResult = remoteStorageSource.queryKeys(keysToQuery, chain.id, null)

        return storageKeyToResult.mapNotNull { (storageKey, hexRaw) ->
            val metadata = allAccountsStorageKeys[storageKey] ?: return@mapNotNull null

            val balanceData = bindEquilibriumAccountData(hexRaw, runtime)
            val equilibriumAssetsBalanceMap = balanceData?.data?.balances.orEmpty()

            chain.assets.map { asset ->
                val balance =
                    asset.currencyId?.toBigInteger()?.let {
                        equilibriumAssetsBalanceMap.getOrDefault(it, null)
                            .orZero()
                    }.orZero()

                AssetBalanceUpdateItem(
                    accountId = metadata.accountId,
                    id = asset.id,
                    chainId = asset.chainId,
                    metaId = metadata.metaAccountId,
                    freeInPlanks = balance
                )
            }
        }.flatten()
    }


    private fun buildEquilibriumStorageKeys(
        chain: Chain,
        runtime: RuntimeSnapshot?,
        metaAccountId: Long,
        accountId: ByteArray
    ): StorageKeyWithMetadata {
        val metadata = StorageKeyWithMetadata(
            requireNotNull(chain.utilityAsset),
            metaAccountId,
            accountId,
            null
        )

        return if (runtime == null) {
            metadata
        } else {
            metadata.copy(
                key = runtime.metadata.system().storage("Account").storageKey(runtime, accountId)
            )
        }
    }

    @SuppressLint("LogNotTimber")
    private fun logError(text: String) {
        Log.d(tag, text)
    }
}
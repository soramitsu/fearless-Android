package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import android.annotation.SuppressLint
import android.util.Log
import jp.co.soramitsu.account.api.domain.model.hasEthereum
import jp.co.soramitsu.account.api.domain.model.hasSubstrate
import jp.co.soramitsu.account.api.domain.model.hasTon
import jp.co.soramitsu.account.impl.data.mappers.mapMetaAccountLocalToMetaAccount
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

private const val TAG = "BalancesUpdateSystem"

@SuppressLint("LogNotTimber")
class BalancesUpdateSystem(
    private val chainRegistry: ChainRegistry,
    private val metaAccountDao: MetaAccountDao,
    private val balanceLoaderProvider: BalanceLoader.Provider,
    private val assetDao: AssetDao
) : UpdateSystem {

    private val scope =
        CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "BalancesUpdateSystem got error: $throwable")
        })

    override fun start(): Flow<Updater.SideEffect> {
        return combine(
            chainRegistry.syncedChains,
            metaAccountDao.selectedMetaAccountInfoFlow().filterNotNull()
        ) { chains, accountInfo ->
//            chainRegistry.configsSyncDeferred.awaitAll()
            scope.coroutineContext.cancelChildren()
            val metaAccount =
                mapMetaAccountLocalToMetaAccount(chains.associateBy { it.id }, accountInfo)

            chains to metaAccount
        }
            .map { (chains, metaAccount) ->
                scope.launch {
                    val supportedChains = chains.filter {
                        it.ecosystem == Ecosystem.Ton && metaAccount.hasTon ||
                                it.ecosystem == Ecosystem.Ethereum && metaAccount.hasEthereum ||
                                it.ecosystem == Ecosystem.Substrate && metaAccount.hasSubstrate
                    }
                    supportedChains.forEach { chain ->
                        launch {
                            val balanceLoader = balanceLoaderProvider.invoke(chain)
                            balanceLoader.subscribeBalance(metaAccount)
                                .collect { balanceLoaderAction ->
                                    when (balanceLoaderAction) {
                                        is BalanceLoader.BalanceLoaderAction.UpdateBalance -> {
                                            assetDao.updateAsset(balanceLoaderAction.balance)
                                        }

                                        is BalanceLoader.BalanceLoaderAction.UpdateOrInsertBalance -> {
                                            val (balance, chainAsset) = balanceLoaderAction
                                            val assetLocal = AssetLocal(
                                                id = balance.id,
                                                chainId = balance.chainId,
                                                accountId = balance.accountId,
                                                metaId = balance.metaId,
                                                tokenPriceId = chainAsset.priceId,
                                                freeInPlanks = balance.freeInPlanks,
                                                reservedInPlanks = balance.reservedInPlanks,
                                                miscFrozenInPlanks = balance.miscFrozenInPlanks,
                                                feeFrozenInPlanks = balance.feeFrozenInPlanks,
                                                bondedInPlanks = balance.bondedInPlanks,
                                                redeemableInPlanks = balance.redeemableInPlanks,
                                                unbondingInPlanks = balance.unbondingInPlanks,
                                                enabled = true
                                            )
                                            assetDao.updateOrInsertAsset(assetLocal)
                                        }
                                    }
                                }
                        }
                    }
                }
            }.transform { }
    }
}

// Request with id = 0 helps to indicate balances subscriptions in logs
class SubscribeBalanceRequest(storageKeys: List<String>) : RuntimeRequest(
    "state_subscribeStorage",
    listOf(storageKeys),
    0
)
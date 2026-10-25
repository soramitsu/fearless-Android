package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import android.annotation.SuppressLint
import android.util.Log
import jp.co.soramitsu.account.api.domain.model.hasChainAccount
import jp.co.soramitsu.account.api.domain.model.hasEthereum
import jp.co.soramitsu.account.api.domain.model.hasSubstrate
import jp.co.soramitsu.account.api.domain.model.hasTon
import jp.co.soramitsu.account.impl.data.mappers.mapMetaAccountLocalToMetaAccount
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletIroha
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.launch

private const val TAG = "BalancesUpdateSystem"

@SuppressLint("LogNotTimber")
class BalancesUpdateSystem(
    private val chainRegistry: ChainRegistry,
    private val metaAccountDao: MetaAccountDao,
    private val balanceLoaderProvider: BalanceLoader.Provider,
    private val assetDao: AssetDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : UpdateSystem {

    override fun start(): Flow<Updater.SideEffect> = flow {
        // Each collector owns its demand. Canceling this flow also retires every
        // balance subscription; a nullable selection explicitly means no demand.
        val workers = CoroutineScope(
            currentCoroutineContext() + dispatcher + SupervisorJob(currentCoroutineContext()[Job]) +
                CoroutineExceptionHandler { _, error ->
                    Log.e(TAG, "Balance subscription failed: ${error.javaClass.simpleName}")
                }
        )
        try {
            combine(chainRegistry.syncedChains, metaAccountDao.selectedMetaAccountInfoFlow()) { chains, accountInfo ->
                chains to accountInfo
            }.collect { (chains, accountInfo) ->
                workers.coroutineContext.cancelChildren()
                if (accountInfo == null) return@collect
                val metaAccount = mapMetaAccountLocalToMetaAccount(chains.associateBy { it.id }, accountInfo)
                workers.launch {
                    supervisorScope {
                        val supportedChains = chains.filter {
                            when {
                                it.isUniversalWalletBitcoin() || it.isUniversalWalletSolana() || it.isUniversalWalletIroha() ->
                                    metaAccount.hasChainAccount(it.id)
                                it.ecosystem == Ecosystem.Ton -> metaAccount.hasTon
                                it.ecosystem == Ecosystem.Ethereum -> metaAccount.hasEthereum
                                it.ecosystem == Ecosystem.Substrate -> metaAccount.hasSubstrate
                                else -> false
                            }
                        }
                        supportedChains.forEach { chain ->
                            launch {
                                balanceLoaderProvider.invoke(chain).subscribeBalance(metaAccount)
                                    .collect { action ->
                                        // A held source may return after cancellation. Never admit its
                                        // stale result to a new DAO mutation. Already-admitted SQL is not rolled back here.
                                        currentCoroutineContext().ensureActive()
                                        when (action) {
                                            is BalanceLoader.BalanceLoaderAction.UpdateBalance -> assetDao.updateAsset(action.balance)
                                            is BalanceLoader.BalanceLoaderAction.UpdateOrInsertBalance -> {
                                                val (balance, chainAsset) = action
                                                assetDao.updateBalanceOrInsertPreservingPreference(balance, chainAsset.priceId)
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        } finally {
            workers.cancel()
        }
    }

}

// Request with id = 0 helps to indicate balances subscriptions in logs
class SubscribeBalanceRequest(storageKeys: List<String>) : RuntimeRequest(
    "state_subscribeStorage",
    listOf(storageKeys),
    0
)

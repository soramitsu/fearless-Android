package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.annotation.SuppressLint
import android.util.Log
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.supervisorScope

@SuppressLint("LogNotTimber")
class EthereumBalanceLoader(
    chain: Chain,
    private val ethereumRemoteSource: EthereumRemoteSource,
    private val scanStateStore: NetworkScanStateStore? = null
) : BalanceLoader(chain) {

    private val trigger = BalanceUpdateTrigger.observe()
    private val tag = "EthereumBalanceLoader (${chain.name})"

    override suspend fun loadBalance(metaAccounts: Set<MetaAccount>): List<AssetBalanceUpdateItem> {
        return supervisorScope {
            val accountsDeferred = metaAccounts.filter { it.ethereumPublicKey != null }.map { metaAccount ->
                async { loadAccountBalances(metaAccount) }
            }
            accountsDeferred.awaitAll().flatten()
        }
    }

    override fun subscribeBalance(metaAccount: MetaAccount): Flow<BalanceLoaderAction> {
        return trigger.onStart { emit(null) }.flatMapLatest { triggeredChainId ->
            channelFlow {
                val specificChainTriggered = triggeredChainId != null
                val currentChainTriggered = triggeredChainId == chain.id

                if (specificChainTriggered && currentChainTriggered.not()) return@channelFlow

                coroutineScope {
                    loadAccountBalances(metaAccount).forEach { balance ->
                        send(BalanceLoaderAction.UpdateBalance(balance))
                    }
                }
            }
        }
    }

    private suspend fun loadAccountBalances(metaAccount: MetaAccount): List<AssetBalanceUpdateItem> {
        val address = metaAccount.address(chain) ?: return emptyList()
        val accountId = metaAccount.accountId(chain) ?: return emptyList()
        scanStateStore?.scanStarted(metaAccount.id, chain.id, AssetDiscoveryCoverage.CatalogOnly)

        val results = coroutineScope {
            chain.assets.map { asset ->
                async {
                    try {
                        EthereumAssetResult(
                            update = AssetBalanceUpdateItem(
                                id = asset.id,
                                chainId = chain.id,
                                accountId = accountId,
                                metaId = metaAccount.id,
                                freeInPlanks = ethereumRemoteSource.fetchEthBalance(asset, address)
                            )
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        runCatching { Log.d(tag, "fetchEthBalance error ${error.message} ${error.localizedMessage} $error") }
                        EthereumAssetResult(error = error)
                    }
                }
            }.awaitAll()
        }

        val failure = results.firstNotNullOfOrNull(EthereumAssetResult::error)
        if (failure == null) {
            scanStateStore?.scanSucceeded(metaAccount.id, chain.id, AssetDiscoveryCoverage.CatalogOnly)
        } else {
            scanStateStore?.scanFailed(
                metaAccount.id,
                chain.id,
                AssetDiscoveryCoverage.CatalogOnly,
                failure.message ?: failure::class.simpleName
            )
        }

        // Failed assets emit no update, preserving their last-known balance.
        return results.mapNotNull(EthereumAssetResult::update)
    }

    private data class EthereumAssetResult(
        val update: AssetBalanceUpdateItem? = null,
        val error: Exception? = null
    )
}

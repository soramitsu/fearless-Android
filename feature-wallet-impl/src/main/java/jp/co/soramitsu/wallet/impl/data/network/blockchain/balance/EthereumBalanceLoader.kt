package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.annotation.SuppressLint
import android.util.Log
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

@SuppressLint("LogNotTimber")
class EthereumBalanceLoader(
    chain: Chain,
    private val ethereumRemoteSource: EthereumRemoteSource
) : BalanceLoader(chain) {

    private val trigger = BalanceUpdateTrigger.observe()
    private val tag = "EthereumBalanceLoader (${chain.name})"

    override suspend fun loadBalance(metaAccounts: Set<MetaAccount>): List<AssetBalanceUpdateItem> {
        return supervisorScope {
            val accountsDeferred = metaAccounts.filter { it.ethereumPublicKey != null }.map { metaAccount ->
                async {
                    val address = metaAccount.address(chain) ?: return@async emptyList()
                    val accountId = metaAccount.accountId(chain) ?: return@async emptyList()
                    val accountBalancesDeferred = chain.assets.map { asset ->
                        async {
                            val balance =
                                kotlin.runCatching {
                                    ethereumRemoteSource.fetchEthBalance(asset, address)
                                }.onFailure {
                                    Log.d(tag, "fetchEthBalance error ${it.message} ${it.localizedMessage} $it")
                                }.getOrNull()

                            AssetBalanceUpdateItem(
                                id = asset.id,
                                chainId = chain.id,
                                accountId = accountId,
                                metaId = metaAccount.id,
                                freeInPlanks = balance
                            )
                        }
                    }
                    accountBalancesDeferred.awaitAll()
                }
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

                val address = metaAccount.address(chain) ?: return@channelFlow
                val accountId = metaAccount.accountId(chain) ?: return@channelFlow
                coroutineScope {
                    chain.assets.onEach { asset ->
                        launch {

                            val balance = kotlin.runCatching {
                                ethereumRemoteSource.fetchEthBalance(asset, address)
                            }.onFailure {
                                Log.d(tag, "fetchEthBalance error ${it.message} ${it.localizedMessage} $it")
                            }.getOrNull() ?: return@launch

                            val assetLocal = AssetBalanceUpdateItem(
                                id = asset.id,
                                chainId = chain.id,
                                accountId = accountId,
                                metaId = metaAccount.id,
                                freeInPlanks = balance
                                //assetBalance.freeInPlanks.positiveOrNull() != null || (!accountHasAssetWithPositiveBalance && isPopularUtilityAsset)
                            )
                            send(BalanceLoaderAction.UpdateBalance(assetLocal))
                        }
                    }
                }
            }
        }
    }
}
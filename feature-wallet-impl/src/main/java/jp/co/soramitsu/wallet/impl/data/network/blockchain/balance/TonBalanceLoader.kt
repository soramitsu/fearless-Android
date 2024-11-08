package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.annotation.SuppressLint
import android.util.Log
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.supervisorScope
import org.ton.block.AddrStd
import java.math.BigInteger

@SuppressLint("LogNotTimber")
class TonBalanceLoader(chain: Chain, private val tonRemoteSource: TonRemoteSource) :
    BalanceLoader(chain) {

    private val trigger = BalanceUpdateTrigger.observe()
    private val tag = "TonBalanceLoader (${chain.name})"

    @SuppressLint("LogNotTimber")
    override suspend fun loadBalance(metaAccounts: Set<MetaAccount>): List<AssetBalanceUpdateItem> {
        return supervisorScope {
            val allAssetsDeferred = metaAccounts.mapNotNull { metaAccount ->
                val accountId = metaAccount.tonPublicKey?.tonAccountId(chain.isTestNet) ?: return@mapNotNull null
                val accountDataDeferred =
                    async { tonRemoteSource.loadAccountData(chain, accountId) }
                val jettonBalancesDeferred =
                    async { tonRemoteSource.loadJettonBalances(chain, accountId) }

                val accountDataResult =
                    kotlin.runCatching { accountDataDeferred.await() }.onFailure {
                        Log.d(tag, "getAccountData failed: $it")
                    }

                val jettonBalancesResult =
                    kotlin.runCatching { jettonBalancesDeferred.await() }.onFailure {
                        Log.d(tag, "getJettonBalances failed: $it")
                    }

                val accountData = accountDataResult.getOrNull()
                val jettonBalances = jettonBalancesResult.getOrNull()

                chain.assets.map { asset ->
                    val balance = when {
                        asset.isUtility -> (accountData?.balance ?: -1).toBigInteger()
                        asset.type == ChainAssetType.Jetton -> {
                            val balanceStr =
                                jettonBalances?.balances?.find { it.jetton.address == asset.id }?.balance
                            kotlin.runCatching { balanceStr?.let { BigInteger(it) } }.onFailure {
                                Log.d(
                                    tag,
                                    "failed to parse jetton balance: $it, str: $balanceStr"
                                )
                            }.getOrNull() ?: BigInteger.valueOf(-1)
                        }

                        else -> BigInteger.valueOf(-1)
                    }

                    AssetBalanceUpdateItem(
                        metaId = metaAccount.id,
                        chainId = chain.id,
                        accountId = metaAccount.tonPublicKey!!,
                        id = asset.id,
                        freeInPlanks = balance,
                    )
                }
            }
            allAssetsDeferred.flatten()
        }
    }

    override fun subscribeBalance(metaAccount: MetaAccount): Flow<AssetBalanceUpdateItem> {
        return trigger.onStart { emit(null) }.flatMapLatest { triggeredChainId ->
            channelFlow {
                val specificChainTriggered = triggeredChainId != null
                val currentChainTriggered = triggeredChainId == chain.id

                if (specificChainTriggered && currentChainTriggered.not()) return@channelFlow

                val accountId = metaAccount.tonPublicKey?.tonAccountId(chain.isTestNet) ?: return@channelFlow

                coroutineScope {
                    val accountDataDeferred =
                        async { tonRemoteSource.loadAccountData(chain, accountId) }
                    val jettonBalancesDeferred =
                        async { tonRemoteSource.loadJettonBalances(chain, accountId) }

                    val accountDataResult =
                        kotlin.runCatching { accountDataDeferred.await() }.onFailure {
                            Log.d(tag, "getAccountData failed: $it")
                        }

                    val jettonBalancesResult =
                        kotlin.runCatching { jettonBalancesDeferred.await() }.onFailure {
                            Log.d(tag, "getJettonBalances failed: $it")
                        }

                    val accountData = accountDataResult.getOrNull()
                    val jettonBalances = jettonBalancesResult.getOrNull()

                    chain.assets.onEach { asset ->
                        val balance = when {
                            asset.isUtility -> (accountData?.balance ?: -1).toBigInteger()
                            asset.type == ChainAssetType.Jetton -> {
                                val balanceStr =
                                    jettonBalances?.balances?.find { it.jetton.address == asset.id }?.balance
                                kotlin.runCatching { balanceStr?.let { BigInteger(it) } }
                                    .onFailure {
                                        Log.d(
                                            tag,
                                            "failed to parse jetton balance: $it, str: $balanceStr"
                                        )
                                    }.getOrNull() ?: BigInteger.valueOf(-1)
                            }

                            else -> BigInteger.valueOf(-1)
                        }

                        val balanceLocal = AssetBalanceUpdateItem(
                            metaId = metaAccount.id,
                            chainId = chain.id,
                            accountId = metaAccount.tonPublicKey!!,
                            id = asset.id,
                            freeInPlanks = balance,
                        )
                        send(balanceLocal)
                    }
                }
            }
        }
    }
}
package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.annotation.SuppressLint
import android.util.Log
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.ext.normalizedBitcoinAddress
import jp.co.soramitsu.runtime.ext.universalWalletBitcoinIndexerNetwork
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.supervisorScope
import java.math.BigInteger

@SuppressLint("LogNotTimber")
class BitcoinBalanceLoader(
    chain: Chain,
    private val bitcoinIndexerClient: BitcoinIndexerClient
) : BalanceLoader(chain) {

    private val trigger = BalanceUpdateTrigger.observe()
    private val tag = "BitcoinBalanceLoader (${chain.name})"

    override suspend fun loadBalance(metaAccounts: Set<MetaAccount>): List<AssetBalanceUpdateItem> {
        return supervisorScope {
            val balancesDeferred = metaAccounts.map { metaAccount ->
                async {
                    loadNativeBalance(metaAccount)
                }
            }

            balancesDeferred.awaitAll().filterNotNull()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun subscribeBalance(metaAccount: MetaAccount): Flow<BalanceLoaderAction> {
        return trigger.onStart { emit(null) }.flatMapLatest { triggeredChainId ->
            channelFlow {
                val specificChainTriggered = triggeredChainId != null
                val currentChainTriggered = triggeredChainId == chain.id

                if (specificChainTriggered && currentChainTriggered.not()) return@channelFlow

                coroutineScope {
                    val balance = loadNativeBalance(metaAccount) ?: return@coroutineScope
                    val asset = nativeBitcoinAsset() ?: return@coroutineScope
                    send(BalanceLoaderAction.UpdateOrInsertBalance(balance, asset))
                }
            }
        }
    }

    private suspend fun loadNativeBalance(metaAccount: MetaAccount): AssetBalanceUpdateItem? {
        val asset = nativeBitcoinAsset() ?: return null
        val accountId = metaAccount.accountId(chain) ?: return null
        val address = metaAccount.address(chain)?.let(chain::normalizedBitcoinAddress) ?: return null
        val network = chain.universalWalletBitcoinIndexerNetwork() ?: return null
        val baseUrl = chain.externalApi?.history?.url

        val balance = runCatching {
            val response = bitcoinIndexerClient.address(
                address = address,
                network = network,
                baseUrl = baseUrl
            )
            response.totalSatsOrNull()
        }.onFailure {
            Log.d(tag, "address balance failed: $it")
        }.getOrNull() ?: return null

        return AssetBalanceUpdateItem(
            metaId = metaAccount.id,
            chainId = chain.id,
            accountId = accountId,
            id = asset.id,
            freeInPlanks = balance
        )
    }

    private fun nativeBitcoinAsset(): Asset? {
        return chain.assets.firstOrNull { asset ->
            asset.isNative == true && asset.symbol.equals(BITCOIN_SYMBOL, ignoreCase = true)
        } ?: chain.assets.firstOrNull { asset ->
            asset.isUtility && asset.symbol.equals(BITCOIN_SYMBOL, ignoreCase = true)
        }
    }

    private fun BitcoinEsploraAddress.totalSatsOrNull(): BigInteger? {
        val confirmed = confirmedSats
        val mempool = mempoolSats
        if (confirmed < 0 || mempool < 0) {
            return null
        }

        return BigInteger.valueOf(confirmed).add(BigInteger.valueOf(mempool))
    }

    private companion object {
        const val BITCOIN_SYMBOL = "BTC"
    }
}

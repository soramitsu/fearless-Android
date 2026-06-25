package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.annotation.SuppressLint
import android.util.Log
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSync
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSyncResult
import jp.co.soramitsu.common.model.UniversalWalletIndexedAssetBalance
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.ext.normalizedSolanaAddress
import jp.co.soramitsu.runtime.ext.universalWalletSolanaIndexerNetwork
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.supervisorScope

@SuppressLint("LogNotTimber")
class SolanaBalanceLoader(
    chain: Chain,
    private val solanaBalanceSync: SolanaBalanceSync
) : BalanceLoader(chain) {

    private val trigger = BalanceUpdateTrigger.observe()
    private val tag = "SolanaBalanceLoader (${chain.name})"

    override suspend fun loadBalance(metaAccounts: Set<MetaAccount>): List<AssetBalanceUpdateItem> {
        return supervisorScope {
            val balancesDeferred = metaAccounts.map { metaAccount ->
                async {
                    loadAssetBalances(metaAccount).map { it.balance }
                }
            }

            balancesDeferred.awaitAll().flatten()
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
                    loadAssetBalances(metaAccount).forEach { update ->
                        send(BalanceLoaderAction.UpdateOrInsertBalance(update.balance, update.asset))
                    }
                }
            }
        }
    }

    private suspend fun loadAssetBalances(metaAccount: MetaAccount): List<SolanaAssetBalanceUpdate> {
        val accountId = metaAccount.accountId(chain) ?: return emptyList()
        val address = metaAccount.address(chain)?.let(chain::normalizedSolanaAddress) ?: return emptyList()
        val network = chain.universalWalletSolanaIndexerNetwork() ?: return emptyList()
        val baseUrl = chain.externalApi?.history?.url

        val result = runCatching {
            solanaBalanceSync.balances(
                wallet = address,
                network = network,
                baseUrl = baseUrl,
                includeTokenMetadata = false
            )
        }.onFailure {
            Log.d(tag, "balance load failed: $it")
        }.getOrNull() ?: return emptyList()

        return chain.assets.mapNotNull { asset ->
            val indexedBalance = indexedBalanceForAsset(asset, network, result) ?: return@mapNotNull null
            val freeInPlanks = indexedBalance.amount.toUnsignedBigIntegerOrNull() ?: return@mapNotNull null
            SolanaAssetBalanceUpdate(
                balance = AssetBalanceUpdateItem(
                    metaId = metaAccount.id,
                    chainId = chain.id,
                    accountId = accountId,
                    id = asset.id,
                    freeInPlanks = freeInPlanks
                ),
                asset = asset
            )
        }
    }

    private fun indexedBalanceForAsset(
        asset: Asset,
        network: jp.co.soramitsu.common.model.UniversalWalletRegistry.SolanaNetwork,
        result: SolanaBalanceSyncResult
    ): UniversalWalletIndexedAssetBalance? {
        if (isNativeSolanaAsset(asset, network)) {
            return result.nativeBalance.takeIf { it.decimals == asset.precision }
        }

        val assetIds = setOfNotNull(asset.id, asset.currencyId)
        return result.tokenBalances.firstOrNull { balance ->
            !balance.isNative &&
                balance.decimals == asset.precision &&
                (balance.assetId in assetIds || balance.contractAddress in assetIds)
        }
    }

    private fun isNativeSolanaAsset(
        asset: Asset,
        network: jp.co.soramitsu.common.model.UniversalWalletRegistry.SolanaNetwork
    ): Boolean {
        return asset.id.equals(network.nativeAsset.id, ignoreCase = true) &&
            asset.symbol.equals(network.nativeAsset.symbol, ignoreCase = true) &&
            asset.precision == network.nativeAsset.decimals &&
            asset.isNative == true
    }

    private fun String.toUnsignedBigIntegerOrNull(): BigInteger? {
        return runCatching { BigInteger(this) }
            .getOrNull()
            ?.takeIf { it.signum() >= 0 }
    }

    private data class SolanaAssetBalanceUpdate(
        val balance: AssetBalanceUpdateItem,
        val asset: Asset
    )
}

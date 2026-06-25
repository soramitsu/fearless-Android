package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.annotation.SuppressLint
import android.util.Log
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.ext.irohaAddressFromPublicKey
import jp.co.soramitsu.runtime.ext.normalizedIrohaAddress
import jp.co.soramitsu.runtime.ext.universalWalletIrohaNetwork
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
class IrohaBalanceLoader(
    chain: Chain,
    private val toriiClient: IrohaToriiClient
) : BalanceLoader(chain) {

    private val trigger = BalanceUpdateTrigger.observe()
    private val tag = "IrohaBalanceLoader (${chain.name})"

    override suspend fun loadBalance(metaAccounts: Set<MetaAccount>): List<AssetBalanceUpdateItem> {
        return supervisorScope {
            metaAccounts.map { metaAccount ->
                async {
                    loadAssetBalances(metaAccount).map { it.balance }
                }
            }.awaitAll().flatten()
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

    private suspend fun loadAssetBalances(metaAccount: MetaAccount): List<IrohaAssetBalanceUpdate> {
        val accountId = metaAccount.accountId(chain) ?: return emptyList()
        val address = metaAccount.address(chain)?.let(chain::normalizedIrohaAddress)
            ?: chain.irohaAddressFromPublicKey(accountId)
            ?: return emptyList()
        val network = chain.universalWalletIrohaNetwork() ?: return emptyList()
        val baseUrl = chain.externalApi?.history
            ?.takeIf { it.type == Chain.ExternalApi.Section.Type.IROHA }
            ?.url

        val response = runCatching {
            toriiClient.accountAssets(
                accountId = address,
                baseUrl = baseUrl,
                limit = IrohaToriiRoutes.MAX_LIMIT,
                countMode = IrohaToriiRoutes.CountMode.Bounded,
                network = network
            )
        }.onFailure {
            Log.d(tag, "balance load failed: $it")
        }.getOrNull() ?: return emptyList()

        return chain.assets.mapNotNull { asset ->
            val freeInPlanks = response.items
                .filter { it.matchesAccount(address) && it.matchesAsset(asset) }
                .mapNotNull { it.quantity.toPlanksOrNull(asset.precision) }
                .takeIf { it.isNotEmpty() }
                ?.fold(BigInteger.ZERO, BigInteger::add)
                ?: return@mapNotNull null

            IrohaAssetBalanceUpdate(
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

    private fun IrohaAccountAssetListItem.matchesAccount(address: String): Boolean {
        val itemAccount = accountId?.takeIf(String::isNotBlank) ?: return true
        return chain.normalizedIrohaAddress(itemAccount) == address
    }

    private fun IrohaAccountAssetListItem.matchesAsset(chainAsset: Asset): Boolean {
        val assetIds = setOfNotNull(chainAsset.id, chainAsset.currencyId)
        val itemIds = setOfNotNull(asset, assetId, assetName, assetAlias)

        return itemIds.any { it in assetIds }
    }

    private fun String.toPlanksOrNull(precision: Int): BigInteger? {
        val normalized = trim()
        if (!DECIMAL_QUANTITY.matches(normalized)) return null

        return runCatching {
            BigDecimal(normalized).movePointRight(precision).toBigIntegerExact()
        }.getOrNull()?.takeIf { it.signum() >= 0 }
    }

    private data class IrohaAssetBalanceUpdate(
        val balance: AssetBalanceUpdateItem,
        val asset: Asset
    )

    private companion object {
        val DECIMAL_QUANTITY = Regex("^(0|[1-9][0-9]*)(\\.[0-9]+)?$")
    }
}

package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.annotation.SuppressLint
import android.util.Log
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
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
import kotlinx.coroutines.CancellationException

@SuppressLint("LogNotTimber")
class IrohaBalanceLoader(
    chain: Chain,
    private val toriiClient: IrohaToriiClient,
    private val persistDiscoveredAssets: suspend (List<Asset>) -> Unit = {},
    private val scanStateStore: NetworkScanStateStore? = null
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

        scanStateStore?.scanStarted(metaAccount.id, chain.id, AssetDiscoveryCoverage.Complete)
        return try {
            val accountAssets = loadAllAccountAssets(address, baseUrl, network)
            accountAssets.forEach { balance ->
                require(balance.quantity.decimalScaleOrNull() != null) {
                    "Malformed Iroha quantity for ${balance.definitionId() ?: "unknown asset"}"
                }
            }

            val knownAssetIds = chain.assets.flatMap { listOfNotNull(it.id, it.currencyId) }.toSet()
            val definitionsById = loadAllAssetDefinitions(baseUrl).flatMap { definition ->
                listOfNotNull(definition.id, definition.name, definition.alias)
                    .map { id -> id to definition }
            }.toMap()
            val discoveredAssets = accountAssets
                .filter { it.matchesAccount(address) }
                .groupBy { it.definitionId() }
                .filterKeys { it != null && it !in knownAssetIds }
                .mapNotNull { (assetId, balances) ->
                    assetId ?: return@mapNotNull null
                    val definition = definitionsById[assetId]
                    val precision = definition?.precisionOrNull()
                        ?: balances.maxOfOrNull { requireNotNull(it.quantity.decimalScaleOrNull()) }
                        ?: 0
                    val total = balances.map { balance ->
                        requireNotNull(balance.quantity.toPlanksOrNull(precision)) {
                            "Iroha quantity precision mismatch for $assetId"
                        }
                    }.fold(BigInteger.ZERO, BigInteger::add)
                    if (total.signum() <= 0) return@mapNotNull null
                    balances.first().toUnverifiedIrohaAsset(assetId, precision, definition)
                }

            val availableAssets = (chain.assets + discoveredAssets).distinctBy(Asset::id)
            val updates = availableAssets.map { asset ->
                val matchingBalances = accountAssets.filter {
                    it.matchesAccount(address) && it.matchesAsset(asset)
                }
                val freeInPlanks = if (matchingBalances.isEmpty()) {
                    // Only a fully parsed, paginated response may establish authoritative
                    // omission and reconcile a previously held definition to zero.
                    BigInteger.ZERO
                } else {
                    matchingBalances.map { balance ->
                        requireNotNull(balance.quantity.toPlanksOrNull(asset.precision)) {
                            "Iroha quantity precision mismatch for ${asset.id}"
                        }
                    }.fold(BigInteger.ZERO, BigInteger::add)
                }

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

            // Validate every quantity and build every update before any persistence side effect.
            persistDiscoveredAssets(discoveredAssets)
            scanStateStore?.scanSucceeded(metaAccount.id, chain.id, AssetDiscoveryCoverage.Complete)
            updates
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            runCatching { Log.d(tag, "balance load failed: $error") }
            scanStateStore?.scanFailed(
                metaAccount.id,
                chain.id,
                AssetDiscoveryCoverage.Complete,
                error.message ?: error::class.simpleName
            )
            // No database updates means the last-known balances remain intact. In particular,
            // a mixed valid/malformed response is never accepted as a partial fresh balance.
            emptyList()
        }
    }

    private suspend fun loadAllAccountAssets(
        address: String,
        baseUrl: String?,
        network: jp.co.soramitsu.common.model.UniversalWalletRegistry.IrohaNetwork
    ): List<IrohaAccountAssetListItem> {
        val result = mutableListOf<IrohaAccountAssetListItem>()
        var offset = 0L
        var pages = 0

        do {
            val response = toriiClient.accountAssets(
                accountId = address,
                baseUrl = baseUrl,
                limit = IrohaToriiRoutes.MAX_LIMIT,
                offset = offset,
                countMode = IrohaToriiRoutes.CountMode.Bounded,
                network = network
            )
            result += response.items
            if (!response.hasMore) break
            check(response.items.isNotEmpty()) { "Iroha pagination returned an empty continuation page" }
            offset += response.items.size
            pages += 1
            check(pages < MAX_PAGES) { "Iroha pagination exceeded the safety limit" }
        } while (true)

        return result
    }

    private suspend fun loadAllAssetDefinitions(
        baseUrl: String?
    ): List<IrohaAssetDefinitionListItem> {
        val result = mutableListOf<IrohaAssetDefinitionListItem>()
        val seenPageSignatures = mutableSetOf<List<String>>()
        var offset = 0L
        var pages = 0

        do {
            val response = toriiClient.assetDefinitionsPage(
                baseUrl = baseUrl,
                limit = IrohaToriiRoutes.MAX_LIMIT,
                offset = offset,
                countMode = IrohaToriiRoutes.CountMode.Bounded
            )
            val signature = response.items.map(IrohaAssetDefinitionListItem::id)
            check(seenPageSignatures.add(signature)) { "Iroha definition pagination repeated a page" }
            result += response.items
            if (!response.hasMore) break
            check(response.items.isNotEmpty()) { "Iroha definition pagination returned an empty continuation page" }
            offset += response.items.size
            pages += 1
            check(pages < MAX_PAGES) { "Iroha definition pagination exceeded the safety limit" }
        } while (true)

        return result
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

    private fun IrohaAccountAssetListItem.definitionId(): String? {
        return listOf(assetId, asset, assetName)
            .firstOrNull { it?.isNotBlank() == true }
    }

    private fun IrohaAccountAssetListItem.toUnverifiedIrohaAsset(
        definitionId: String,
        precision: Int,
        definition: IrohaAssetDefinitionListItem?
    ): Asset {
        val safeName = definition?.name?.takeIf(String::isNotBlank)
            ?: assetName?.takeIf(String::isNotBlank)
        val safeSymbol = definition?.alias?.takeIf(String::isNotBlank)
            ?: assetAlias?.takeIf(String::isNotBlank)
            ?: definitionId.substringBefore('#').takeIf(String::isNotBlank)
            ?: definitionId

        return Asset(
            id = definitionId,
            name = safeName,
            symbol = safeSymbol,
            iconUrl = "",
            chainId = chain.id,
            chainName = chain.name,
            chainIcon = chain.icon,
            isTestNet = chain.isTestNet,
            priceId = null,
            precision = precision,
            staking = Asset.StakingType.UNSUPPORTED,
            purchaseProviders = null,
            supportStakingPool = false,
            isUtility = false,
            type = ChainAssetType.Unknown,
            currencyId = definitionId,
            existentialDeposit = null,
            color = null,
            isNative = false
        )
    }

    private fun IrohaAssetDefinitionListItem.precisionOrNull(): Int? {
        val raw = metadata?.entries?.firstOrNull { (key, _) ->
            key.equals("precision", ignoreCase = true) ||
                key.equals("scale", ignoreCase = true) ||
                key.equals("decimals", ignoreCase = true)
        }?.value ?: return null

        val precision = when (raw) {
            is Byte -> raw.toInt()
            is Short -> raw.toInt()
            is Int -> raw
            is Long -> raw.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
            is Float -> raw.takeIf { it % 1f == 0f }?.toInt()
            is Double -> raw.takeIf { it % 1.0 == 0.0 }?.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
        return precision?.takeIf { it in 0..255 }
    }

    private fun String.decimalScaleOrNull(): Int? {
        if (!DECIMAL_QUANTITY.matches(trim())) return null
        return runCatching { BigDecimal(trim()).scale().coerceAtLeast(0) }.getOrNull()
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
        const val MAX_PAGES = 1_000
    }
}

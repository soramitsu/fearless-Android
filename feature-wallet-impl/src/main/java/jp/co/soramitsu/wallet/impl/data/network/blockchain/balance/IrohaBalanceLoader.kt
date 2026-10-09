package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.annotation.SuppressLint
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.common.model.UniversalWalletRegistry
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
        val network = chain.universalWalletIrohaNetwork()
            ?: throw IrohaAssetConfigurationException(
                "Wallet chain ${chain.id} is not a canonical Iroha network"
            )
        val accountId = metaAccount.accountId(chain) ?: return emptyList()
        val address = metaAccount.address(chain)?.let(chain::normalizedIrohaAddress)
            ?: chain.irohaAddressFromPublicKey(accountId)
            ?: return emptyList()
        val baseUrl = chain.externalApi?.history
            ?.takeIf { it.type == Chain.ExternalApi.Section.Type.IROHA }
            ?.url

        val definitions = toriiClient.assetDefinitions(
            baseUrl = baseUrl,
            limit = IrohaToriiRoutes.MAX_LIMIT,
            offset = 0,
            countMode = IrohaToriiRoutes.CountMode.Bounded,
            network = network
        )
        if (
            definitions.hasMore != false ||
            definitions.countMode != IrohaToriiRoutes.CountMode.Bounded.apiValue
        ) {
            throw IrohaAssetConfigurationException(
                "Torii did not prove a complete asset-definition snapshot"
            )
        }
        val definitionsById = definitions.items.associateByCanonicalId()
        val response = toriiClient.accountAssets(
            accountId = address,
            baseUrl = baseUrl,
            limit = IrohaToriiRoutes.MAX_LIMIT,
            offset = 0,
            countMode = IrohaToriiRoutes.CountMode.Bounded,
            network = network
        )
        if (
            response.hasMore != false ||
            response.countMode != IrohaToriiRoutes.CountMode.Bounded.apiValue
        ) {
            throw IrohaAssetConfigurationException(
                "Torii did not prove a complete account-asset snapshot"
            )
        }
        val seenAccountAssetScopes = mutableSetOf<Pair<String, String>>()
        response.items.forEach { item ->
            if (item.accountId != address) {
                throw IrohaAssetConfigurationException(
                    "Torii returned an account-asset row for an unexpected account"
                )
            }
            if (item.assetId != null) {
                throw IrohaAssetConfigurationException(
                    "Torii returned the retired asset_id account-asset field"
                )
            }
            val itemAsset: String? = item.asset
            val presentItemAsset = itemAsset
                ?: throw IrohaAssetConfigurationException(
                    "Torii omitted the canonical account-asset id"
                )
            val canonicalItemAsset = try {
                IrohaToriiRoutes.normalizeAssetDefinitionId(presentItemAsset)
            } catch (_: IllegalArgumentException) {
                throw IrohaAssetConfigurationException(
                    "Torii returned a non-canonical account-asset id: $presentItemAsset"
                )
            }
            if (canonicalItemAsset != presentItemAsset) {
                throw IrohaAssetConfigurationException(
                    "Torii returned a non-canonical account-asset id: $presentItemAsset"
                )
            }
            val itemScope = item.scope
                ?: throw IrohaAssetConfigurationException(
                    "Torii omitted the canonical account-asset scope"
                )
            val canonicalScope = try {
                IrohaToriiRoutes.normalizeAccountAssetScope(itemScope)
            } catch (_: IllegalArgumentException) {
                throw IrohaAssetConfigurationException(
                    "Torii returned a non-canonical account-asset scope: $itemScope"
                )
            }
            if (canonicalScope != itemScope) {
                throw IrohaAssetConfigurationException(
                    "Torii returned a non-canonical account-asset scope: $itemScope"
                )
            }
            if (!seenAccountAssetScopes.add(canonicalItemAsset to canonicalScope)) {
                throw IrohaAssetConfigurationException(
                    "Torii returned a duplicate account-asset scope for $canonicalItemAsset"
                )
            }
        }

        return chain.assets.map { asset ->
            val canonicalAssetId = try {
                IrohaToriiRoutes.normalizeAssetDefinitionId(asset.id)
            } catch (_: IllegalArgumentException) {
                throw IrohaAssetConfigurationException(
                    "Wallet asset ${asset.id} is not a canonical Iroha asset definition id"
                )
            }
            val definition = definitionsById[canonicalAssetId]
                ?: throw IrohaAssetConfigurationException(
                    "Torii has no asset definition for wallet asset $canonicalAssetId"
                )
            val profilePrecision = network.validatedWalletPrecision(asset, canonicalAssetId)
            val precision = definition.validatedPrecision(profilePrecision)
            val maxBalanceInPlanks = MAX_QUANTITY.multiply(BigInteger.TEN.pow(precision))
            val freeInPlanks = response.items
                .filter { it.asset == canonicalAssetId }
                .map { item ->
                    val quantity: String? = item.quantity
                    quantity?.toPlanksOrNull(precision)
                        ?: throw IrohaAssetConfigurationException(
                            "Torii returned a quantity that cannot be represented at scale $precision"
                        )
                }
                .fold(BigInteger.ZERO) { total, value ->
                    if (total > maxBalanceInPlanks || value > maxBalanceInPlanks.subtract(total)) {
                        throw IrohaAssetConfigurationException(
                            "Torii account-asset quantity sum exceeds the canonical Numeric domain"
                        )
                    }
                    total.add(value)
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
    }

    private fun List<IrohaAssetDefinitionListItem>.associateByCanonicalId(): Map<String, IrohaAssetDefinitionListItem> {
        val result = linkedMapOf<String, IrohaAssetDefinitionListItem>()
        for (definition in this) {
            val canonicalId = try {
                IrohaToriiRoutes.normalizeAssetDefinitionId(definition.id)
            } catch (_: IllegalArgumentException) {
                throw IrohaAssetConfigurationException(
                    "Torii returned a non-canonical asset definition id: ${definition.id}"
                )
            }
            if (result.put(canonicalId, definition) != null) {
                throw IrohaAssetConfigurationException("Torii returned duplicate asset definition $canonicalId")
            }
        }
        return result
    }

    private fun IrohaAssetDefinitionListItem.validatedPrecision(walletPrecision: Int): Int {
        val chainScale = spec?.scale
            ?: throw IrohaAssetConfigurationException(
                "Torii omitted the fixed scale for wallet asset $id"
            )
        if (chainScale != walletPrecision) {
            throw IrohaAssetConfigurationException(
                "Wallet precision $walletPrecision does not match Torii scale $chainScale for $id"
            )
        }

        return chainScale
    }

    private fun UniversalWalletRegistry.IrohaNetwork.validatedWalletPrecision(
        asset: Asset,
        canonicalAssetId: String
    ): Int {
        val profileAsset = nativeAsset?.takeIf { it.id == canonicalAssetId } ?: return asset.precision
        if (asset.symbol != profileAsset.symbol || asset.precision != profileAsset.decimals) {
            throw IrohaAssetConfigurationException(
                "Wallet native profile ${asset.symbol}/${asset.precision} does not match " +
                    "${profileAsset.symbol}/${profileAsset.decimals} for $canonicalAssetId"
            )
        }

        return profileAsset.decimals
    }

    private fun String.toPlanksOrNull(precision: Int): BigInteger? {
        if (this != trim() || precision !in 0..MAX_SUPPORTED_PRECISION || !DECIMAL_QUANTITY.matches(this)) {
            return null
        }
        val parts = split('.', limit = 2)
        val fraction = parts.getOrElse(1) { "" }
        val digits = parts[0] + fraction
        if (fraction.length > precision || digits.length > MAX_QUANTITY_DIGITS) return null
        val mantissa = runCatching { BigInteger(digits) }.getOrNull()
            ?.takeIf { it.signum() >= 0 && it <= MAX_QUANTITY }
            ?: return null

        return mantissa.multiply(BigInteger.TEN.pow(precision - fraction.length))
    }

    private data class IrohaAssetBalanceUpdate(
        val balance: AssetBalanceUpdateItem,
        val asset: Asset
    )

    private companion object {
        const val MAX_SUPPORTED_PRECISION = 28
        const val MAX_QUANTITY_DIGITS = 154
        val MAX_QUANTITY = BigInteger.ONE.shiftLeft(511).subtract(BigInteger.ONE)
        val DECIMAL_QUANTITY = Regex("^(0|[1-9][0-9]*)(\\.[0-9]*[1-9])?$")
    }
}

class IrohaAssetConfigurationException(message: String) : IllegalStateException(message)

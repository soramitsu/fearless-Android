package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.annotation.SuppressLint
import android.util.Log
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSync
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSyncResult
import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.common.model.UniversalWalletIndexedAssetBalance
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
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
import kotlinx.coroutines.CancellationException

@SuppressLint("LogNotTimber")
class SolanaBalanceLoader(
    chain: Chain,
    private val solanaBalanceSync: SolanaBalanceSync,
    private val persistDiscoveredAssets: suspend (List<Asset>) -> Unit = {},
    private val scanStateStore: NetworkScanStateStore? = null
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

        scanStateStore?.scanStarted(metaAccount.id, chain.id, AssetDiscoveryCoverage.Complete)
        val result = try {
            solanaBalanceSync.balances(
                wallet = address,
                network = network,
                baseUrl = baseUrl,
                includeTokenMetadata = true
            )
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
            // No database updates means the last-known balances remain intact.
            return emptyList()
        }

        return try {
            validateAuthoritativeResult(address, network, result)
            val discoveredAssets = result.tokenBalances
                .filter { balance -> requireNotNull(balance.amount.toUnsignedBigIntegerOrNull()).signum() == 1 }
                .filterNot { balance -> chain.assets.any { it.matchesIndexedBalance(balance) } }
                .groupBy(UniversalWalletIndexedAssetBalance::assetId)
                .values
                .map { balances -> balances.first().toUnverifiedSolanaAsset() }
            val availableAssets = (chain.assets + discoveredAssets).distinctBy(Asset::id)
            val updates = availableAssets.map { asset ->
                val indexedBalance = indexedBalanceForAsset(asset, network, result)
                val freeInPlanks = when {
                    indexedBalance != null -> requireNotNull(
                        indexedBalance.amount.toUnsignedBigIntegerOrNull()
                    ) { INVALID_BALANCE_PAYLOAD }
                    hasRawBalanceEntry(asset, network, result) -> error(INVALID_BALANCE_PAYLOAD)
                    // A fully validated complete response omitted this known token. Reconcile its
                    // prior persisted balance to zero; malformed responses fail before this point.
                    else -> BigInteger.ZERO
                }
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

            // Validate and materialize all updates before either persistence or freshness changes.
            persistDiscoveredAssets(discoveredAssets)
            scanStateStore?.scanSucceeded(metaAccount.id, chain.id, AssetDiscoveryCoverage.Complete)
            updates
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            runCatching { Log.d(tag, "balance payload or persistence failed: $error") }
            scanStateStore?.scanFailed(
                metaAccount.id,
                chain.id,
                AssetDiscoveryCoverage.Complete,
                error.message ?: error::class.simpleName
            )
            emptyList()
        }
    }

    private fun validateAuthoritativeResult(
        address: String,
        network: jp.co.soramitsu.common.model.UniversalWalletRegistry.SolanaNetwork,
        result: SolanaBalanceSyncResult
    ) {
        val expectedBalances = listOf(result.nativeBalance) + result.tokenBalances
        require(
            result.wallet == address &&
                result.networkId == network.id &&
                result.chainId == network.chainId &&
                result.syncedAtMillis > 0 &&
                result.balances == expectedBalances &&
                result.nativeBalance.isNative &&
                result.nativeBalance.assetId == network.nativeAsset.id &&
                result.nativeBalance.decimals == network.nativeAsset.decimals &&
                result.nativeBalance.amount.toUnsignedBigIntegerOrNull() != null &&
                result.nativeBalance.validationErrors().isEmpty()
        ) { INVALID_BALANCE_PAYLOAD }

        result.tokenBalances.forEach { balance ->
            require(
                !balance.isNative &&
                    balance.accountId == network.id &&
                    balance.chainId == network.chainId &&
                    balance.syncedAtMillis == result.syncedAtMillis &&
                    balance.contractAddress == balance.assetId &&
                    balance.amount.toUnsignedBigIntegerOrNull() != null &&
                    balance.validationErrors().isEmpty()
            ) { INVALID_BALANCE_PAYLOAD }
        }
        require(
            result.tokenBalances.groupBy(UniversalWalletIndexedAssetBalance::assetId)
                .values
                .all { balances -> balances.map { it.decimals }.distinct().size == 1 }
        ) { INVALID_BALANCE_PAYLOAD }

        chain.assets.forEach { asset ->
            if (matchesNativeSolanaIdentity(asset, network)) {
                require(asset.precision == result.nativeBalance.decimals) { INVALID_BALANCE_PAYLOAD }
            } else {
                val matchingBalances = result.tokenBalances.filter { balance ->
                    asset.matchesIndexedBalance(balance)
                }
                require(matchingBalances.all { it.decimals == asset.precision }) {
                    INVALID_BALANCE_PAYLOAD
                }
            }
        }
    }

    private fun Asset.matchesIndexedBalance(balance: UniversalWalletIndexedAssetBalance): Boolean {
        val assetIds = setOfNotNull(id, currencyId)
        return balance.assetId in assetIds || balance.contractAddress in assetIds
    }

    private fun UniversalWalletIndexedAssetBalance.toUnverifiedSolanaAsset(): Asset {
        return Asset(
            id = assetId,
            name = name?.takeUnless { it == assetId },
            symbol = symbol?.takeIf(String::isNotBlank) ?: assetId,
            iconUrl = "",
            chainId = chain.id,
            chainName = chain.name,
            chainIcon = chain.icon,
            isTestNet = chain.isTestNet,
            // Dynamic metadata and ticker text cannot confer price identity.
            priceId = null,
            precision = decimals,
            staking = Asset.StakingType.UNSUPPORTED,
            purchaseProviders = null,
            supportStakingPool = false,
            isUtility = false,
            type = ChainAssetType.Unknown,
            currencyId = assetId,
            existentialDeposit = null,
            color = null,
            isNative = false
        )
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
        val matchingBalances = result.tokenBalances.filter { balance ->
            !balance.isNative &&
                (balance.assetId in assetIds || balance.contractAddress in assetIds)
        }
        if (matchingBalances.isEmpty()) return null
        if (matchingBalances.any { it.decimals != asset.precision }) return null

        val total = matchingBalances.map { balance ->
            balance.amount.toUnsignedBigIntegerOrNull() ?: return null
        }.fold(BigInteger.ZERO, BigInteger::add)

        return matchingBalances.first().copy(amount = total.toString())
    }

    private fun hasRawBalanceEntry(
        asset: Asset,
        network: jp.co.soramitsu.common.model.UniversalWalletRegistry.SolanaNetwork,
        result: SolanaBalanceSyncResult
    ): Boolean {
        if (matchesNativeSolanaIdentity(asset, network)) return true

        val assetIds = setOfNotNull(asset.id, asset.currencyId)
        return result.tokenBalances.any { balance ->
            !balance.isNative &&
                (balance.assetId in assetIds || balance.contractAddress in assetIds)
        }
    }

    private fun isNativeSolanaAsset(
        asset: Asset,
        network: jp.co.soramitsu.common.model.UniversalWalletRegistry.SolanaNetwork
    ): Boolean {
        return matchesNativeSolanaIdentity(asset, network) &&
            asset.precision == network.nativeAsset.decimals
    }

    private fun matchesNativeSolanaIdentity(
        asset: Asset,
        network: jp.co.soramitsu.common.model.UniversalWalletRegistry.SolanaNetwork
    ): Boolean {
        return asset.id.equals(network.nativeAsset.id, ignoreCase = true) &&
            asset.symbol.equals(network.nativeAsset.symbol, ignoreCase = true) &&
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

    private companion object {
        const val INVALID_BALANCE_PAYLOAD = "Invalid Solana balance payload"
    }
}

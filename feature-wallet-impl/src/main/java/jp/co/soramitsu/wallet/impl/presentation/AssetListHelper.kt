package jp.co.soramitsu.wallet.impl.presentation

import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.model.AssetMetadataSource
import jp.co.soramitsu.common.model.AssetMetadataDescriptor
import jp.co.soramitsu.common.model.AssetMetadataTrust
import jp.co.soramitsu.common.model.AssetPreference
import jp.co.soramitsu.common.model.CanonicalAssetIdentity
import jp.co.soramitsu.common.model.PriceTrust
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.runtime.ext.assetKey
import jp.co.soramitsu.runtime.ext.isUniversalWalletIroha
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.isDetectedAsset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioExclusions
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioPresentationPreferences
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.BalanceListItemModel

/**
 * List helpers for the portfolio. Which rows are visible and which are counted is decided by the
 * shared [jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility] rule; callers pass
 * only counted items to [trustedFiatSubtotal] and as valuation items to [sortByNetwork].
 */
object AssetListHelper {

    /** Shadow rollout affects review presentation only; discovery and persistence stay active. */
    fun filterForPortfolioPresentation(
        assets: List<BalanceListItemModel>,
        assetDiscoveryShadowEnabled: Boolean
    ): List<BalanceListItemModel> {
        if (!assetDiscoveryShadowEnabled) return assets

        return assets.filterNot {
            it.preference == AssetPreference.Auto &&
                it.metadataTrust != AssetMetadataTrust.Verified
        }
    }

    fun trustedFiatSubtotal(assets: List<BalanceListItemModel>): java.math.BigDecimal? {
        val trusted = assets.filter {
            it.total?.signum() == 1 &&
                it.metadataTrust == AssetMetadataTrust.Verified &&
                it.priceTrust != PriceTrust.Untrusted &&
                it.fiatAmount != null
        }
        if (trusted.isEmpty()) return null

        return trusted.sumOf { it.fiatAmount.orZero() }
    }

    fun processAssets(
        assets: List<AssetWithStatus>,
        filteredChains: List<Chain>,
        selectedChainId: ChainId? = null,
        metadataDescriptor: (CanonicalAssetIdentity) -> AssetMetadataDescriptor? = { null },
        isTestnet: (Chain) -> Boolean = Chain::isTestNet,
        hasTrustedPrice: (chainId: ChainId, assetId: String) -> Boolean = { _, _ -> true }
    ): List<BalanceListItemModel> {
        val chainsById = filteredChains.associateBy(Chain::id)

        return assets.mapNotNull { assetWithStatus ->
            if (assetWithStatus.hasAccount.not()) return@mapNotNull null

            val capturedAsset = assetWithStatus.asset
            val chain = chainsById[capturedAsset.token.configuration.chainId]
                ?: return@mapNotNull null
            if (selectedChainId != null && chain.id != selectedChainId) return@mapNotNull null

            val chainAsset = chain.assetsById[capturedAsset.token.configuration.id]
                ?: return@mapNotNull null
            // Catalog and amount streams can deliver independently. A changed configuration
            // cannot relabel a previously captured value; retain an unavailable row until refresh.
            val walletAsset = if (capturedAsset.token.configuration == chainAsset) capturedAsset else {
                jp.co.soramitsu.wallet.impl.domain.model.Asset.createEmpty(
                    chainAsset, capturedAsset.metaId, capturedAsset.accountId.copyOf(),
                    capturedAsset.chainAccountName, chain.minSupportedVersion
                ).copy(enabled = capturedAsset.enabled, sortIndex = capturedAsset.sortIndex,
                    markedNotNeed = capturedAsset.markedNotNeed)
            }
            val isDetectedAsset = chainAsset.isDetectedAsset()
            val identity = chain.assetKey(chainAsset)
            val storedMetadata = metadataDescriptor(identity)
            val resolvedMetadataTrust = storedMetadata?.trust ?: if (isDetectedAsset) {
                if (chainAsset.name == null) AssetMetadataTrust.Missing else AssetMetadataTrust.Unverified
            } else {
                AssetMetadataTrust.Verified
            }
            val resolvedMetadataSource = storedMetadata?.source ?: if (isDetectedAsset) {
                if (chain.isUniversalWalletSolana() || chain.isUniversalWalletIroha()) {
                    AssetMetadataSource.Indexer
                } else {
                    AssetMetadataSource.Chain
                }
            } else {
                AssetMetadataSource.Registry
            }
            val isTestnetChain = isTestnet(chain)
            // The bundled untrustedAssets list overrides the registry: a listed asset has no price.
            // Neither has a testnet token: it has no value, so its row never shows one.
            val hasTrustedExactPrice = resolvedMetadataTrust == AssetMetadataTrust.Verified &&
                !isTestnetChain &&
                chainAsset.priceId != null &&
                hasTrustedPrice(chain.id, chainAsset.id) &&
                walletAsset.token.configuration.id == chainAsset.id &&
                walletAsset.token.configuration.chainId == chain.id &&
                walletAsset.token.fiatRate != null
            val trustedToken = if (hasTrustedExactPrice) {
                walletAsset.token
            } else {
                walletAsset.token.copy(
                    fiatRateInput = AssetAmountState.Unavailable("untrusted-price"),
                    recentRateChangeInput = AssetAmountState.Unavailable("untrusted-price")
                )
            }

            BalanceListItemModel(
                asset = chainAsset,
                chain = chain,
                token = trustedToken,
                total = walletAsset.total,
                fiatAmount = walletAsset.fiatAmount.takeIf { hasTrustedExactPrice },
                transferable = walletAsset.transferable,
                chainUrls = mapOf(chain.id to chain.icon),
                chainAccountName = walletAsset.chainAccountName,
                isHidden = walletAsset.enabled == false,
                preference = when (walletAsset.enabled) {
                    true -> AssetPreference.Shown
                    false -> AssetPreference.Hidden
                    null -> AssetPreference.Auto
                },
                canonicalIdentity = identity,
                metadataTrust = resolvedMetadataTrust,
                metadataSource = resolvedMetadataSource,
                priceTrust = if (hasTrustedExactPrice) {
                    PriceTrust.Canonical
                } else {
                    PriceTrust.Untrusted
                },
                isTestnet = isTestnetChain
            )
        }
    }

    /**
     * What the wallet-wide total leaves out (contract §3). [countedItems] are the holdings the
     * total counts (neither hidden nor on a testnet) and [hiddenItems] the explicitly hidden ones
     * outside testnets, both for the whole wallet. Only verified assets are reported.
     */
    fun portfolioExclusions(
        countedItems: List<BalanceListItemModel>,
        hiddenItems: List<BalanceListItemModel>
    ): PortfolioExclusions {
        fun List<BalanceListItemModel>.verifiedHoldings() = filter {
            it.metadataTrust == AssetMetadataTrust.Verified && it.total?.signum() == 1
        }
        val counted = countedItems.verifiedHoldings()

        return PortfolioExclusions(
            hiddenFundedAssetKeys = hiddenItems.verifiedHoldings().mapNotNullTo(mutableSetOf()) { item ->
                item.chain?.let { chain -> PortfolioPresentationPreferences.assetKey(chain.id, item.asset.id) }
            },
            unpricedFundedCount = counted.count { it.fiatAmount == null },
            hiddenAssetCount = hiddenItems.size,
            hiddenFiat = trustedFiatSubtotal(hiddenItems)?.formatFiat(hiddenItems.firstOrNull()?.token?.fiatSymbol.orEmpty()),
            poolStakesExcluded = counted.any { item ->
                (item.asset.isUtility || item.asset.isNative == true) && item.chain?.supportStakingPool == true
            }
        )
    }

    /**
     * Networks by value, then networks that hold something without a price, then empty networks
     * in registry rank order, so a new wallet opens on the popular networks instead of the
     * alphabet.
     */
    fun sortByNetwork(
        assets: List<BalanceListItemModel>,
        valuationAssets: List<BalanceListItemModel> = assets
    ): List<BalanceListItemModel> {
        val valuationsByNetwork = valuationAssets.groupBy { it.canonicalIdentity.chainId }
        return assets.groupBy { it.canonicalIdentity.chainId }
            .values
            .sortedWith(
                compareByDescending<List<BalanceListItemModel>> { networkAssets ->
                    trustedFiatSubtotal(
                        valuationsByNetwork[networkAssets.first().canonicalIdentity.chainId].orEmpty()
                    ) != null
                }.thenByDescending { networkAssets ->
                    trustedFiatSubtotal(
                        valuationsByNetwork[networkAssets.first().canonicalIdentity.chainId].orEmpty()
                    ).orZero()
                }.thenByDescending { networkAssets ->
                    networkAssets.any { it.total?.signum() == 1 }
                }.thenBy { networkAssets ->
                    networkAssets.firstOrNull()?.chain?.rank ?: Int.MAX_VALUE
                }.thenBy { networkAssets ->
                    networkAssets.firstOrNull()?.let { first ->
                        first.chain?.name?.takeIf(String::isNotBlank)
                            ?: first.canonicalIdentity.chainId
                    }.orEmpty()
                }
            )
            .flatMap { networkAssets ->
                networkAssets.sortedWith(
                    compareByDescending<BalanceListItemModel> { it.total?.signum() == 1 }
                        .thenByDescending { it.fiatAmount.orZero() }
                        .thenBy { it.asset.symbol }
                )
            }
    }

}

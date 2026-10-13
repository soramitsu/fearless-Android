package jp.co.soramitsu.wallet.impl.presentation

import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.model.AssetMetadataSource
import jp.co.soramitsu.common.model.AssetMetadataDescriptor
import jp.co.soramitsu.common.model.AssetMetadataTrust
import jp.co.soramitsu.common.model.AssetPreference
import jp.co.soramitsu.common.model.CanonicalAssetIdentity
import jp.co.soramitsu.common.model.PriceTrust
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.runtime.ext.assetKey
import jp.co.soramitsu.runtime.ext.isUniversalWalletIroha
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.BalanceListItemModel

object AssetListHelper {

    fun shouldDisplayInPortfolio(
        total: java.math.BigDecimal,
        asset: jp.co.soramitsu.core.models.Asset,
        isPinnedNetwork: Boolean,
        isDefaultNetwork: Boolean
    ): Boolean {
        val pinnedOrDefaultNative = asset.isNative == true &&
            (isPinnedNetwork || isDefaultNetwork)

        return total > java.math.BigDecimal.ZERO || pinnedOrDefaultNative
    }

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
            it.total > java.math.BigDecimal.ZERO &&
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
    ): List<BalanceListItemModel> {
        val chainsById = filteredChains.associateBy(Chain::id)

        return assets.mapNotNull { assetWithStatus ->
            if (assetWithStatus.hasAccount.not()) return@mapNotNull null

            val walletAsset = assetWithStatus.asset
            val chain = chainsById[walletAsset.token.configuration.chainId]
                ?: return@mapNotNull null
            if (selectedChainId != null && chain.id != selectedChainId) return@mapNotNull null

            val chainAsset = chain.assetsById[walletAsset.token.configuration.id]
                ?: return@mapNotNull null
            val isDetectedAsset = chainAsset.type == ChainAssetType.Unknown ||
                (chainAsset.type == ChainAssetType.Jetton &&
                    chainAsset.isUtility.not() && chainAsset.priceId == null)
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
            val hasTrustedExactPrice = resolvedMetadataTrust == AssetMetadataTrust.Verified &&
                chainAsset.priceId != null &&
                walletAsset.token.configuration.id == chainAsset.id &&
                walletAsset.token.configuration.chainId == chain.id &&
                walletAsset.token.fiatRate != null
            val trustedToken = if (hasTrustedExactPrice) {
                walletAsset.token
            } else {
                walletAsset.token.copy(fiatRate = null, recentRateChange = null)
            }

            BalanceListItemModel(
                asset = chainAsset,
                chain = chain,
                token = trustedToken,
                total = walletAsset.total.orZero(),
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
                }
            )
        }
    }

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
                }.thenBy { networkAssets ->
                    networkAssets.firstOrNull()?.let { first ->
                        first.chain?.name?.takeIf(String::isNotBlank)
                            ?: first.canonicalIdentity.chainId
                    }.orEmpty()
                }
            )
            .flatMap { networkAssets ->
                networkAssets.sortedWith(
                    compareByDescending<BalanceListItemModel> { it.total > java.math.BigDecimal.ZERO }
                        .thenByDescending { it.fiatAmount.orZero() }
                        .thenBy { it.asset.symbol }
                )
            }
    }

}

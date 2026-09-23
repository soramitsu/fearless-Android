package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.xcm.domain.CrossChainRouteAvailability
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability

enum class CrossChainRouteInventoryStatus {
    Available,
    ActionsPaused,
    SetupRequired,
    Unavailable
}

data class CrossChainRouteInventoryNetwork(
    val id: ChainId,
    val name: String
)

data class CrossChainRouteInventoryAsset(
    val originNetworkId: ChainId,
    val originNetworkName: String,
    val originAssetId: String,
    val symbol: String
)

/** Read-only presentation state. Inventory rows deliberately expose no click or execution action. */
data class CrossChainRouteInventoryItem(
    val providerId: String,
    val protocolName: String,
    val status: CrossChainRouteInventoryStatus,
    val originNetworks: List<CrossChainRouteInventoryNetwork>,
    val destinationNetworks: List<CrossChainRouteInventoryNetwork>,
    val assets: List<CrossChainRouteInventoryAsset>,
    val minimumAmount: String?,
    val minimumAssetSymbol: String?,
    val estimatedTime: String?,
    val warnings: List<String>,
    val userFacingReason: String?
) {
    val hasCatalogCoverage: Boolean
        get() = originNetworks.isNotEmpty() && destinationNetworks.isNotEmpty() && assets.isNotEmpty()

    val isExactRoute: Boolean
        get() = originNetworks.size == 1 &&
            destinationNetworks.size == 1 &&
            assets.size == 1 &&
            assets.single().originNetworkId == originNetworks.single().id
}

internal suspend fun buildCrossChainRouteInventory(
    capabilities: List<CrossChainRouteCapability>,
    networkName: suspend (ChainId) -> String
): List<CrossChainRouteInventoryItem> {
    val networkNames = mutableMapOf<ChainId, String>()

    suspend fun resolveNetwork(chainId: ChainId): CrossChainRouteInventoryNetwork {
        val name = networkNames[chainId] ?: networkName(chainId)
            .ifBlank { chainId }
            .also { networkNames[chainId] = it }
        return CrossChainRouteInventoryNetwork(chainId, name)
    }

    return capabilities.map { capability ->
        val origins = capability.supportedOriginNetworkIds.map { resolveNetwork(it) }
        val destinations = capability.supportedDestinationNetworkIds.map { resolveNetwork(it) }
        val assets = capability.supportedAssets.map { asset ->
            CrossChainRouteInventoryAsset(
                originNetworkId = asset.originNetworkId,
                originNetworkName = resolveNetwork(asset.originNetworkId).name,
                originAssetId = asset.originAssetId,
                symbol = asset.symbol
            )
        }
        val status = when {
            capability.availability == CrossChainRouteAvailability.Available &&
                capability.actionsEnabled -> CrossChainRouteInventoryStatus.Available
            capability.availability == CrossChainRouteAvailability.Available ->
                CrossChainRouteInventoryStatus.ActionsPaused
            capability.availability == CrossChainRouteAvailability.SetupRequired ->
                CrossChainRouteInventoryStatus.SetupRequired
            else -> CrossChainRouteInventoryStatus.Unavailable
        }

        CrossChainRouteInventoryItem(
            providerId = capability.providerId,
            protocolName = capability.protocol.displayName,
            status = status,
            originNetworks = origins,
            destinationNetworks = destinations,
            assets = assets,
            minimumAmount = capability.minimumAmount,
            minimumAssetSymbol = capability.minimumAssetSymbol,
            estimatedTime = capability.estimatedTime,
            warnings = capability.warnings,
            userFacingReason = capability.userFacingReason
        )
    }
}

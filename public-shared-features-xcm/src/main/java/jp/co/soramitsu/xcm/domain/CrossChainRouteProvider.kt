package jp.co.soramitsu.xcm.domain

import jp.co.soramitsu.core.models.ChainId
import java.math.BigDecimal

enum class CrossChainProtocol(val displayName: String) {
    ReviewedXcm("reviewed XCM"),
    PolkaswapSoraEvm("Polkaswap SORA/EVM bridge"),
    PolkaswapSoraSubstrate("Polkaswap SORA/Substrate bridge"),
    Liberland("Liberland bridge")
}

enum class CrossChainRouteAvailability {
    Available,
    SetupRequired,
    Unavailable
}

data class CrossChainRouteQuery(
    val originNetworkId: ChainId? = null,
    val destinationNetworkId: ChainId? = null,
    val originAssetId: String? = null,
    /** Presentation-only hint. It never participates in route matching. */
    val assetSymbol: String? = null
)

data class CrossChainAssetIdentity(
    val originNetworkId: ChainId,
    val originAssetId: String,
    val symbol: String
) {
    init {
        require(originNetworkId.isNotBlank()) { "Cross-chain origin network id must not be blank" }
        require(originAssetId.isNotBlank()) { "Cross-chain origin asset id must not be blank" }
        require(symbol.isNotBlank()) { "Cross-chain asset symbol must not be blank" }
    }
}

/**
 * An exact, immutable route description. Descriptors disclose reviewed catalog coverage only; the
 * provider capability still decides whether an executor exists and whether mutations are enabled.
 */
data class CrossChainRouteDescriptor(
    val originNetworkId: ChainId,
    val destinationNetworkId: ChainId,
    val asset: CrossChainAssetIdentity,
    /** Arbitrary-precision decimal string in [CrossChainAssetIdentity.symbol] units. */
    val minimumAmount: String? = null,
    val estimatedTime: String? = null,
    val warnings: List<String> = emptyList()
) {
    init {
        require(destinationNetworkId.isNotBlank()) {
            "Cross-chain destination network id must not be blank"
        }
        require(asset.originNetworkId == originNetworkId) {
            "Cross-chain descriptor asset must belong to its origin network"
        }
        require(minimumAmount == null || minimumAmount.toBigDecimalOrNull() != null) {
            "Cross-chain descriptor minimum must be a decimal string"
        }
        require(warnings.none(String::isBlank)) { "Cross-chain descriptor warnings must not be blank" }
    }
}

data class CrossChainFeeQuote(
    /** Arbitrary-precision decimal string in [assetSymbol] units. */
    val amount: String,
    val assetSymbol: String,
    val live: Boolean
) {
    init {
        require(amount.toBigDecimalOrNull() != null) { "Cross-chain fee must be a decimal string" }
        require(assetSymbol.isNotBlank()) { "Cross-chain fee asset symbol must not be blank" }
    }
}

/** Immutable provider authority carried from setup into confirmation. */
data class CrossChainProviderContext(
    val providerId: String,
    val routeId: String,
    val runtimeFingerprint: String
) {
    init {
        require(providerId.isNotBlank()) { "Cross-chain provider context requires a provider id" }
        require(routeId.isNotBlank()) { "Cross-chain provider context requires a route id" }
        require(runtimeFingerprint.isNotBlank()) {
            "Cross-chain provider context requires a runtime fingerprint"
        }
    }
}

/**
 * Provider-level capability data used before opening or mutating a route.
 *
 * Supported networks and assets are descriptive discovery data. [actionsEnabled] is an
 * independent kill switch: disabling writes never removes the Cross-chain destination.
 */
data class CrossChainRouteCapability(
    val providerId: String,
    val protocol: CrossChainProtocol,
    val availability: CrossChainRouteAvailability,
    val actionsEnabled: Boolean,
    val requiresAccount: Boolean,
    val requiresSigning: Boolean,
    val supportedOriginNetworkIds: Set<ChainId> = emptySet(),
    val supportedDestinationNetworkIds: Set<ChainId> = emptySet(),
    val supportedAssets: Set<CrossChainAssetIdentity> = emptySet(),
    /** Arbitrary-precision decimal string in [minimumAssetSymbol] units. */
    val minimumAmount: String? = null,
    val minimumAssetSymbol: String? = null,
    val originFee: CrossChainFeeQuote? = null,
    val destinationFee: CrossChainFeeQuote? = null,
    /** Present only after an exact reviewed route has been resolved against the live runtime. */
    val providerContext: CrossChainProviderContext? = null,
    /** Provider-supplied human-readable estimate. Null means no reviewed estimate exists. */
    val estimatedTime: String? = null,
    val warnings: List<String> = emptyList(),
    val userFacingReason: String? = null
) {
    init {
        require(providerId.isNotBlank()) { "Cross-chain provider id must not be blank" }
        require(availability == CrossChainRouteAvailability.Available || userFacingReason != null) {
            "Unavailable cross-chain capability must explain why"
        }
        require(actionsEnabled || userFacingReason != null) {
            "Disabled cross-chain actions must explain why"
        }
        require(minimumAmount == null || minimumAmount.toBigDecimalOrNull() != null) {
            "Cross-chain minimum must be a decimal string"
        }
        val hasMinimumAmount = minimumAmount != null
        val hasMinimumAssetSymbol = minimumAssetSymbol != null
        require(hasMinimumAmount == hasMinimumAssetSymbol) {
            "Cross-chain minimum amount and asset symbol must be supplied together"
        }
        require(warnings.none(String::isBlank)) { "Cross-chain warnings must not be blank" }
        require(providerContext == null || providerContext.providerId == providerId) {
            "Cross-chain provider context does not belong to this provider"
        }
        require(providerContext == null || availability == CrossChainRouteAvailability.Available) {
            "Only an available exact route may expose provider execution context"
        }
    }

    val hasRoute: Boolean get() = availability == CrossChainRouteAvailability.Available
    val canExecute: Boolean get() = hasRoute && actionsEnabled
    val supportedAssetSymbols: Set<String> get() = supportedAssets.mapTo(linkedSetOf()) { it.symbol }
}

interface CrossChainRouteProvider {
    val providerId: String
    val protocol: CrossChainProtocol

    suspend fun capability(query: CrossChainRouteQuery = CrossChainRouteQuery()): CrossChainRouteCapability

    /**
     * Read-only inventory shown at the Cross-chain root. Providers with a route catalog may
     * override this to return one exact capability per catalog entry. Inventory never authorizes
     * execution; [CrossChainRouteCapability.actionsEnabled] remains the final provider decision.
     */
    suspend fun inventoryCapabilities(): List<CrossChainRouteCapability> = listOf(capability())
}

/** Reviewed wallet XCM provider backed only by the immutable/effective route registry. */
@Suppress("ClassOrdering")
class ReviewedWalletXcmRouteProvider(
    private val entitiesFetcher: XcmEntitiesFetcher,
    private val actionsEnabled: () -> Boolean,
    private val actionsDisabledReason: () -> String
) : CrossChainRouteProvider {

    constructor(
        entitiesFetcher: XcmEntitiesFetcher,
        actionsEnabled: Boolean,
        actionsDisabledReason: String
    ) : this(
        entitiesFetcher = entitiesFetcher,
        actionsEnabled = { actionsEnabled },
        actionsDisabledReason = { actionsDisabledReason }
    )

    override val providerId: String = PROVIDER_ID
    override val protocol: CrossChainProtocol = CrossChainProtocol.ReviewedXcm

    // Keep distinct route, asset, and authorization rejections fail-closed.
    @Suppress("CyclomaticComplexMethod")
    override suspend fun capability(query: CrossChainRouteQuery): CrossChainRouteCapability {
        val actionsAreEnabled = actionsEnabled()
        val hasSpecificAssetHint = query.assetSymbol != null || query.originAssetId != null
        if (
            hasSpecificAssetHint &&
            (query.originNetworkId == null || query.originAssetId.isNullOrBlank())
        ) {
            return CrossChainRouteCapability(
                providerId = providerId,
                protocol = protocol,
                availability = CrossChainRouteAvailability.SetupRequired,
                actionsEnabled = false,
                requiresAccount = true,
                requiresSigning = true,
                userFacingReason = "Select a network-scoped asset with an exact asset id."
            )
        }

        val origins = entitiesFetcher.getAvailableOriginChains(
            assetSymbol = null,
            destinationChainId = query.destinationNetworkId,
            originAssetId = query.originAssetId
        ).toSet()
        val destinations = entitiesFetcher.getAvailableDestinationChains(
            originChainId = query.originNetworkId,
            assetSymbol = null,
            originAssetId = query.originAssetId
        ).toSet()
        val assets = entitiesFetcher.getAvailableAssets(
            originChainId = query.originNetworkId,
            destinationChainId = query.destinationNetworkId,
            originAssetId = query.originAssetId
        )
        val routeMatches = origins.isNotEmpty() && destinations.isNotEmpty() && assets.isNotEmpty() &&
            (query.originNetworkId == null || query.originNetworkId in origins) &&
            (query.destinationNetworkId == null || query.destinationNetworkId in destinations)
        val reason = when {
            !actionsAreEnabled -> actionsDisabledReason()
            !routeMatches -> "No reviewed XCM route matches the selected networks and asset."
            else -> null
        }
        val exactRouteAsset = assets.singleOrNull().takeIf {
            query.originNetworkId != null &&
                query.destinationNetworkId != null &&
                !query.originAssetId.isNullOrBlank()
        }
        val minimumAmount = exactRouteAsset?.minAmount?.let { minimumInPlanks ->
            val precision = requireNotNull(exactRouteAsset.originAssetPrecision) {
                "Reviewed XCM route is missing origin asset precision"
            }
            BigDecimal(minimumInPlanks, precision).stripTrailingZeros().toPlainString()
        }
        val warnings = buildList {
            if (routeMatches && exactRouteAsset != null) {
                add("Estimated delivery time is not supplied by the reviewed XCM provider.")
            }
        }

        return CrossChainRouteCapability(
            providerId = providerId,
            protocol = protocol,
            availability = if (routeMatches) {
                CrossChainRouteAvailability.Available
            } else {
                CrossChainRouteAvailability.Unavailable
            },
            actionsEnabled = actionsAreEnabled && routeMatches,
            requiresAccount = true,
            requiresSigning = true,
            supportedOriginNetworkIds = origins,
            supportedDestinationNetworkIds = destinations,
            supportedAssets = assets.mapNotNullTo(linkedSetOf()) { asset ->
                val originNetworkId = asset.originChainId ?: return@mapNotNullTo null
                val originAssetId = asset.originAssetId ?: return@mapNotNullTo null
                CrossChainAssetIdentity(
                    originNetworkId = originNetworkId,
                    originAssetId = originAssetId,
                    symbol = asset.symbol.normalizedRouteSymbol()
                )
            },
            minimumAmount = minimumAmount,
            minimumAssetSymbol = minimumAmount?.let { exactRouteAsset?.symbol },
            estimatedTime = null,
            warnings = warnings,
            userFacingReason = reason
        )
    }

    private fun String.normalizedRouteSymbol(): String = trim()
        .removePrefix("xc")
        .removePrefix("XC")
        .uppercase()

    companion object {
        const val PROVIDER_ID = "wallet-reviewed-xcm"
    }
}

/** A visible provider declaration for a known protocol that has no reviewed executor yet. */
class UnavailableCrossChainRouteProvider(
    override val providerId: String,
    override val protocol: CrossChainProtocol,
    private val reason: String,
    private val routeDescriptors: List<CrossChainRouteDescriptor> = emptyList()
) : CrossChainRouteProvider {
    init {
        require(reason.isNotBlank()) { "Unavailable cross-chain provider must explain why" }
        require(routeDescriptors.distinct().size == routeDescriptors.size) {
            "Unavailable cross-chain route descriptors must be unique"
        }
    }

    override suspend fun capability(query: CrossChainRouteQuery): CrossChainRouteCapability {
        val hasSpecificAssetHint = query.assetSymbol != null || query.originAssetId != null
        if (
            hasSpecificAssetHint &&
            (query.originNetworkId == null || query.originAssetId.isNullOrBlank())
        ) {
            return CrossChainRouteCapability(
                providerId = providerId,
                protocol = protocol,
                availability = CrossChainRouteAvailability.SetupRequired,
                actionsEnabled = false,
                requiresAccount = true,
                requiresSigning = true,
                userFacingReason = "Select a network-scoped asset with an exact asset id."
            )
        }

        val matching = routeDescriptors.filter { descriptor ->
            (query.originNetworkId == null || descriptor.originNetworkId == query.originNetworkId) &&
                (
                    query.destinationNetworkId == null ||
                    descriptor.destinationNetworkId == query.destinationNetworkId
                ) &&
                (query.originAssetId == null || descriptor.asset.originAssetId == query.originAssetId)
        }
        val exact = matching.singleOrNull().takeIf {
            query.originNetworkId != null &&
                query.destinationNetworkId != null &&
                !query.originAssetId.isNullOrBlank()
        }

        return CrossChainRouteCapability(
            providerId = providerId,
            protocol = protocol,
            availability = CrossChainRouteAvailability.Unavailable,
            actionsEnabled = false,
            requiresAccount = true,
            requiresSigning = true,
            supportedOriginNetworkIds = matching.mapTo(linkedSetOf()) { it.originNetworkId },
            supportedDestinationNetworkIds = matching.mapTo(linkedSetOf()) { it.destinationNetworkId },
            supportedAssets = matching.mapTo(linkedSetOf()) { it.asset },
            minimumAmount = exact?.minimumAmount,
            minimumAssetSymbol = exact?.minimumAmount?.let { exact.asset.symbol },
            estimatedTime = exact?.estimatedTime,
            warnings = matching.flatMapTo(linkedSetOf()) { it.warnings }.toList(),
            userFacingReason = reason
        )
    }

    override suspend fun inventoryCapabilities(): List<CrossChainRouteCapability> {
        if (routeDescriptors.isEmpty()) return listOf(capability())

        return routeDescriptors.map { descriptor ->
            capability(
                CrossChainRouteQuery(
                    originNetworkId = descriptor.originNetworkId,
                    destinationNetworkId = descriptor.destinationNetworkId,
                    originAssetId = descriptor.asset.originAssetId
                )
            )
        }
    }
}

class CrossChainRouteProviderRegistry(
    private val providers: List<CrossChainRouteProvider>
) {
    init {
        require(providers.isNotEmpty()) { "At least one cross-chain provider must be declared" }
        require(providers.map(CrossChainRouteProvider::providerId).distinct().size == providers.size) {
            "Cross-chain provider ids must be unique"
        }
    }

    suspend fun capabilities(query: CrossChainRouteQuery = CrossChainRouteQuery()): List<CrossChainRouteCapability> =
        providers.map { provider ->
            runCatching { provider.capability(query) }.getOrElse { error ->
                provider.failureCapability(error)
            }
        }

    /** Every declared provider remains represented even when its catalog lookup fails. */
    suspend fun inventoryCapabilities(): List<CrossChainRouteCapability> = buildList {
        providers.forEach { provider ->
            val inventory = runCatching { provider.inventoryCapabilities() }
                .getOrElse { error -> listOf(provider.failureCapability(error)) }
            addAll(
                inventory.ifEmpty {
                listOf(
                    provider.failureCapability(
                        IllegalStateException("${provider.protocol.displayName} has no catalog entries.")
                    )
                )
            }
            )
        }
    }

    suspend fun bestCapability(query: CrossChainRouteQuery = CrossChainRouteQuery()): CrossChainRouteCapability {
        val capabilities = capabilities(query)
        return capabilities.firstOrNull(CrossChainRouteCapability::canExecute)
            ?: capabilities.firstOrNull(CrossChainRouteCapability::hasRoute)
            ?: capabilities.first()
    }

    fun requireProvider(providerId: String): CrossChainRouteProvider =
        providers.singleOrNull { it.providerId == providerId }
            ?: throw IllegalArgumentException("Unknown cross-chain provider: $providerId")

    private fun CrossChainRouteProvider.failureCapability(error: Throwable) = CrossChainRouteCapability(
        providerId = providerId,
        protocol = protocol,
        availability = CrossChainRouteAvailability.Unavailable,
        actionsEnabled = false,
        requiresAccount = true,
        requiresSigning = true,
        userFacingReason = error.message ?: "${protocol.displayName} is unavailable."
    )
}

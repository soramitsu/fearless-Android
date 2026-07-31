package jp.co.soramitsu.xcm.domain

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.XcmDiscoverySnapshotProvider
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigInteger

data class XcmAsset @JvmOverloads constructor(
    val id: String?,
    val symbol: String,
    val minAmount: BigInteger?,
    val originChainId: ChainId? = null,
    val originAssetId: String? = null,
    val originAssetPrecision: Int? = null
)

data class XcmExecutableRoute(
    val asset: XcmAsset,
    val executionSpec: XcmExecutionSpec,
    val originIdentity: XcmRouteChainIdentity,
    val destinationIdentity: XcmRouteChainIdentity
)

data class XcmRouteChainIdentity(
    val chainId: ChainId,
    val parentId: ChainId?,
    val paraId: String?,
    val addressPrefix: Int,
    val ecosystem: Ecosystem,
    val isEthereumBased: Boolean,
    val isEthereumChain: Boolean,
    val isTestNet: Boolean
) {
    internal fun matches(chain: Chain): Boolean {
        return chain.id == chainId &&
            chain.parentId == parentId &&
            chain.paraId == paraId &&
            chain.addressPrefix == addressPrefix &&
            chain.ecosystem == ecosystem &&
            chain.isEthereumBased == isEthereumBased &&
            chain.isEthereumChain == isEthereumChain &&
            chain.isTestNet == isTestNet
    }
}

class XcmEntitiesFetcher internal constructor(
    private val chainsProvider: suspend () -> List<Chain>,
    private val approvedRoutes: ApprovedXcmRouteRegistry
) {
    /**
     * Compatibility constructor for consumers that do not provide APK-reviewed
     * route authority. It deliberately exposes no executable or discoverable routes.
     */
    @Suppress("UNUSED_PARAMETER")
    constructor(chainRegistry: ChainRegistry) : this(
        chainsProvider = { emptyList() },
        approvedRoutes = ApprovedXcmRouteRegistry.unavailable()
    )

    constructor(
        discoverySnapshotProvider: XcmDiscoverySnapshotProvider,
        approvedRoutes: ApprovedXcmRouteRegistry
    ) : this(
        { discoverySnapshotProvider.getCurrentProcessXcmDiscoveryChains() },
        approvedRoutes
    )

    suspend fun getAvailableOriginChains(assetSymbol: String?, destinationChainId: ChainId?): List<ChainId> {
        val normalizedAssetSymbol = assetSymbol?.normalizedXcmSymbol()

        return effectiveRoutes()
            .asSequence()
            .filter { destinationChainId == null || it.destinationChainId == destinationChainId }
            .filter { normalizedAssetSymbol == null || it.asset.symbol == normalizedAssetSymbol }
            .map { it.originChainId }
            .distinct()
            .toList()
    }

    suspend fun getAvailableAssets(originChainId: ChainId?, destinationChainId: ChainId?): List<XcmAsset> {
        return effectiveRoutes()
            .asSequence()
            .filter { originChainId == null || it.originChainId == originChainId }
            .filter { destinationChainId == null || it.destinationChainId == destinationChainId }
            .map { it.asset }
            .distinctBy { Triple(it.originChainId, it.originAssetId, it.symbol) }
            .toList()
    }

    suspend fun getAvailableDestinationChains(originChainId: ChainId?, assetSymbol: String?): List<ChainId> {
        val normalizedAssetSymbol = assetSymbol?.normalizedXcmSymbol()

        return effectiveRoutes()
            .asSequence()
            .filter { originChainId == null || it.originChainId == originChainId }
            .filter { normalizedAssetSymbol == null || it.asset.symbol == normalizedAssetSymbol }
            .map { it.destinationChainId }
            .distinct()
            .toList()
    }

    suspend fun getMinAmount(
        originChainId: ChainId,
        destinationChainId: ChainId,
        assetSymbol: String
    ): BigInteger? = getRouteAsset(originChainId, destinationChainId, assetSymbol)?.minAmount

    suspend fun getRouteAsset(
        originChainId: ChainId,
        destinationChainId: ChainId,
        assetSymbol: String
    ): XcmAsset? {
        val normalizedAssetSymbol = assetSymbol.normalizedXcmSymbol()

        return effectiveRoutes().firstOrNull {
            it.originChainId == originChainId &&
                it.destinationChainId == destinationChainId &&
                it.asset.symbol == normalizedAssetSymbol
        }?.asset
    }

    suspend fun getExecutableRoute(
        originChainId: ChainId,
        destinationChainId: ChainId,
        assetSymbol: String
    ): XcmExecutableRoute? {
        val normalizedAssetSymbol = assetSymbol.normalizedXcmSymbol()
        return effectiveRoutes().firstOrNull {
            it.originChainId == originChainId &&
                it.destinationChainId == destinationChainId &&
                it.asset.symbol == normalizedAssetSymbol
        }?.let {
            XcmExecutableRoute(
                asset = it.asset,
                executionSpec = it.executionSpec,
                originIdentity = it.originIdentity,
                destinationIdentity = it.destinationIdentity
            )
        }
    }

    suspend fun hasExecutableRouteAsset(originChainId: ChainId, assetSymbol: String): Boolean {
        val normalizedAssetSymbol = assetSymbol.normalizedXcmSymbol()

        return effectiveRoutes().any {
            it.originChainId == originChainId && it.asset.symbol == normalizedAssetSymbol
        }
    }

    private suspend fun effectiveRoutes(): List<EffectiveXcmRoute> {
        return approvedRoutes.effectiveRoutes(chainsProvider())
    }

    private fun String.normalizedXcmSymbol(): String = trim()
        .lowercase()
        .removedXcPrefix()
        .uppercase()
}

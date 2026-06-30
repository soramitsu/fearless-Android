package jp.co.soramitsu.xcm.domain

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigInteger

data class XcmAsset(
    val id: String?,
    val symbol: String,
    val minAmount: BigInteger?
)

data class XcmExecutableRoute(
    val asset: XcmAsset,
    val executionSpec: XcmExecutionSpec
)

class XcmEntitiesFetcher internal constructor(
    private val chainsProvider: suspend () -> List<Chain>
) {
    constructor(chainRegistry: ChainRegistry) : this({ chainRegistry.getChains() })

    suspend fun getAvailableOriginChains(assetSymbol: String?, destinationChainId: ChainId?): List<ChainId> {
        val normalizedAssetSymbol = assetSymbol?.normalizedXcmSymbol()

        return chainsProvider()
            .asSequence()
            .filter { chain -> chain.xcm != null }
            .filter { chain ->
                destinationChainId == null ||
                    chain.xcm?.availableDestinations.orEmpty().any { it.chainId == destinationChainId }
            }
            .filter { chain ->
                normalizedAssetSymbol == null ||
                    chain.assetsForDestination(destinationChainId).any { it.normalizedSymbol == normalizedAssetSymbol }
            }
            .map { it.id }
            .distinct()
            .toList()
    }

    suspend fun getAvailableAssets(originChainId: ChainId?, destinationChainId: ChainId?): List<XcmAsset> {
        return chainsProvider()
            .asSequence()
            .filter { originChainId == null || it.id == originChainId }
            .flatMap { it.assetsForDestination(destinationChainId).asSequence() }
            .distinctBy { it.normalizedSymbol }
            .map { it.asset }
            .toList()
    }

    suspend fun getAvailableDestinationChains(originChainId: ChainId?, assetSymbol: String?): List<ChainId> {
        val normalizedAssetSymbol = assetSymbol?.normalizedXcmSymbol()

        return chainsProvider()
            .asSequence()
            .filter { originChainId == null || it.id == originChainId }
            .flatMap { chain ->
                chain.xcm?.availableDestinations.orEmpty().asSequence()
                    .filter { destination ->
                        normalizedAssetSymbol == null ||
                            destination.assets.orEmpty().mapNotNull { it.toDomainAsset() }.any {
                                it.normalizedSymbol == normalizedAssetSymbol
                            }
                    }
                    .mapNotNull { it.chainId?.takeIf(String::isNotBlank) }
            }
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

        return chainsProvider()
            .firstOrNull { it.id == originChainId }
            ?.assetsForDestination(destinationChainId)
            ?.firstOrNull { it.normalizedSymbol == normalizedAssetSymbol }
            ?.asset
    }

    suspend fun getExecutableRoute(
        originChainId: ChainId,
        destinationChainId: ChainId,
        assetSymbol: String
    ): XcmExecutableRoute? {
        val normalizedAssetSymbol = assetSymbol.normalizedXcmSymbol()
        val originChain = chainsProvider().firstOrNull { it.id == originChainId }
        val xcm = originChain?.xcm
        val destination = xcm?.availableDestinations.orEmpty()
            .firstOrNull { it.chainId == destinationChainId }
        val routeAsset = destination?.assets.orEmpty()
            .mapNotNull { it.toDomainAsset() }
            .firstOrNull { it.normalizedSymbol == normalizedAssetSymbol }
            ?.asset

        return destination?.let { executableDestination ->
            routeAsset?.let { asset ->
                XcmExecutableRoute(
                    asset = asset,
                    executionSpec = XcmExecutionSpecValidator.requireValid(
                        originChainId = originChainId,
                        destinationChainId = destinationChainId,
                        assetSymbol = asset.symbol,
                        xcmVersion = xcm?.xcmVersion,
                        destination = executableDestination
                    )
                )
            }
        }
    }

    suspend fun hasExecutableRouteAsset(originChainId: ChainId, assetSymbol: String): Boolean {
        val normalizedAssetSymbol = assetSymbol.normalizedXcmSymbol()
        val originChain = chainsProvider().firstOrNull { it.id == originChainId } ?: return false
        val xcm = originChain.xcm ?: return false

        return xcm.availableDestinations.orEmpty().any { destination ->
            val destinationChainId = destination.chainId?.takeIf(String::isNotBlank) ?: return@any false

            destination.assets.orEmpty()
                .mapNotNull { it.toDomainAsset() }
                .any { routeAsset ->
                    routeAsset.normalizedSymbol == normalizedAssetSymbol &&
                        runCatching {
                            XcmExecutionSpecValidator.requireValid(
                                originChainId = originChainId,
                                destinationChainId = destinationChainId,
                                assetSymbol = routeAsset.asset.symbol,
                                xcmVersion = xcm.xcmVersion,
                                destination = destination
                            )
                        }.isSuccess
                }
        }
    }

    private fun Chain.assetsForDestination(destinationChainId: ChainId?): List<NormalizedXcmAsset> {
        val xcm = xcm ?: return emptyList()

        val routeAssets = xcm.availableDestinations.orEmpty()
            .asSequence()
            .filter { destinationChainId == null || it.chainId == destinationChainId }
            .flatMap { it.assets.orEmpty().asSequence() }
            .mapNotNull { it.toDomainAsset() }
            .toList()

        return when {
            destinationChainId != null -> routeAssets
            routeAssets.isNotEmpty() -> routeAssets
            else -> xcm.availableAssets.orEmpty().mapNotNull { it.toDomainAsset() }
        }
    }

    private fun Chain.Xcm.Asset.toDomainAsset(): NormalizedXcmAsset? {
        val normalizedSymbol = symbol?.normalizedXcmSymbol()?.takeIf(String::isNotBlank) ?: return null
        val parsedMinAmount = minAmount
            ?.takeIf(String::isNotBlank)
            ?.toBigIntegerOrNull()
            ?.takeIf { it >= BigInteger.ZERO }

        return NormalizedXcmAsset(
            normalizedSymbol = normalizedSymbol,
            asset = XcmAsset(
                id = id,
                symbol = normalizedSymbol,
                minAmount = parsedMinAmount
            )
        )
    }

    private data class NormalizedXcmAsset(
        val normalizedSymbol: String,
        val asset: XcmAsset
    )

    private fun String.normalizedXcmSymbol(): String = trim()
        .lowercase()
        .removedXcPrefix()
        .uppercase()
}

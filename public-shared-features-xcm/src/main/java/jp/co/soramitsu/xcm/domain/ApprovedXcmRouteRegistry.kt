package jp.co.soramitsu.xcm.domain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import java.math.BigInteger
import java.util.Locale

/**
 * Immutable transaction-authority data shipped inside the APK.
 *
 * Remote chain configuration remains useful for route discovery, but it is
 * mutable and therefore must never supply an execution call. A route is
 * effective only when the current discovery data exactly matches one of these
 * reviewed per-asset approvals.
 */
class ApprovedXcmRouteRegistry private constructor(
    private val approvedRoutes: LinkedHashMap<ApprovedXcmRouteKey, ApprovedXcmRoute>
) {

    internal fun effectiveRoutes(chains: List<Chain>): List<EffectiveXcmRoute> {
        val chainsById = chains.groupBy { it.id }
        if (chainsById.any { (chainId, matches) -> chainId.isBlank() || matches.size != 1 }) {
            return emptyList()
        }

        return approvedRoutes.values.mapNotNull { approved ->
            runCatching { approved.resolve(chainsById) }.getOrNull()
        }
    }

    internal fun approvedRouteCount(): Int = approvedRoutes.size

    companion object {
        fun unavailable(): ApprovedXcmRouteRegistry = ApprovedXcmRouteRegistry(linkedMapOf())

        internal fun fromReviewedChains(
            reviewedChains: List<Chain>,
            routeKeys: List<ApprovedXcmRouteKey>
        ): ApprovedXcmRouteRegistry {
            require(reviewedChains.isNotEmpty()) { "Approved XCM chain registry must not be empty" }
            require(routeKeys.isNotEmpty()) { "Approved XCM route allowlist must not be empty" }

            val chainsById = reviewedChains.groupBy { it.id }
            require(chainsById.none { (chainId, matches) -> chainId.isBlank() || matches.size != 1 }) {
                "Approved XCM chain registry contains blank or duplicate chain ids"
            }
            reviewedChains.forEach(::requireUnambiguousPerAssetExecution)

            val routes = linkedMapOf<ApprovedXcmRouteKey, ApprovedXcmRoute>()
            routeKeys.forEach { requestedKey ->
                val key = requestedKey.normalized()
                require(routes[key] == null) {
                    "Duplicate approved XCM route ${key.originChainId} -> ${key.destinationChainId} ${key.assetSymbol}"
                }

                val origin = requireNotNull(chainsById[key.originChainId]?.single()) {
                    "Approved XCM origin chain missing: ${key.originChainId}"
                }
                val destinationChain = requireNotNull(chainsById[key.destinationChainId]?.single()) {
                    "Approved XCM destination chain missing: ${key.destinationChainId}"
                }
                val originXcm = requireNotNull(origin.xcm) {
                    "Approved XCM origin metadata missing: ${key.originChainId}"
                }
                val destinations = originXcm.availableDestinations.orEmpty().filter {
                    it.chainId.normalizedRequiredIdentity() == key.destinationChainId
                }
                require(destinations.size == 1) {
                    "Approved XCM destination must occur exactly once: ${key.originChainId} -> ${key.destinationChainId}"
                }

                val destination = destinations.single()
                val assets = destination.assets.orEmpty()
                val matchingAssets = assets.filter {
                    runCatching { it.symbol.normalizedXcmSymbol() }.getOrNull() == key.assetSymbol
                }
                require(matchingAssets.size == 1) {
                    "Approved XCM destination must contain exactly one matching route asset: ${key.originChainId} -> ${key.destinationChainId} ${key.assetSymbol}"
                }
                val asset = matchingAssets.single()
                val normalizedAssetSymbol = asset.symbol.normalizedXcmSymbol()
                val assetId = asset.id.normalizedRequiredIdentity()
                val minAmount = parseOptionalMinAmount(asset.minAmount)
                val originAssets = origin.assets.filter {
                    runCatching { it.symbol.normalizedXcmSymbol() }.getOrNull() == normalizedAssetSymbol
                }
                require(originAssets.size == 1) {
                    "Approved XCM origin must contain exactly one core asset for $normalizedAssetSymbol on ${key.originChainId}"
                }
                val originAsset = originAssets.single()
                val originAssetId = originAsset.id.normalizedRequiredIdentity()
                require(originAsset.precision >= 0) {
                    "Approved XCM origin core asset precision must not be negative for ${key.originChainId} $normalizedAssetSymbol"
                }
                val xcmVersion = originXcm.xcmVersion.normalizedRequiredIdentity()
                val executionSpec = XcmExecutionSpecValidator.requireValid(
                    originChainId = key.originChainId,
                    destinationChainId = key.destinationChainId,
                    assetSymbol = normalizedAssetSymbol,
                    xcmVersion = xcmVersion,
                    destination = destination,
                    asset = asset
                )
                require(!origin.isTestNet && !destinationChain.isTestNet) {
                    "Approved production XCM routes must use mainnet origin and destination chains"
                }
                require(origin.ecosystem == Ecosystem.Substrate || origin.ecosystem == Ecosystem.EthereumBased) {
                    "Approved XCM origin chain must use a Substrate-compatible ecosystem"
                }
                require(origin.isEthereumBased == (origin.ecosystem == Ecosystem.EthereumBased)) {
                    "Approved XCM origin ecosystem and account-width metadata must agree"
                }
                require(destinationChain.isEthereumBased == (destinationChain.ecosystem == Ecosystem.EthereumBased)) {
                    "Approved XCM destination ecosystem and account-width metadata must agree"
                }
                val beneficiaryAccountType = executionSpec.beneficiaryLocation.junctions
                    .single { it.type == XcmJunctionType.ACCOUNT_ID32 || it.type == XcmJunctionType.ACCOUNT_KEY20 }
                    .type
                when (destinationChain.ecosystem) {
                    Ecosystem.Substrate -> require(beneficiaryAccountType == XcmJunctionType.ACCOUNT_ID32) {
                        "Approved Substrate XCM destination requires an AccountId32 beneficiary"
                    }
                    Ecosystem.EthereumBased -> require(beneficiaryAccountType == XcmJunctionType.ACCOUNT_KEY20) {
                        "Approved Ethereum-based XCM destination requires an AccountKey20 beneficiary"
                    }
                    else -> throw IllegalArgumentException(
                        "Approved XCM destination chain must use a Substrate-compatible ecosystem"
                    )
                }
                require(executionSpec.destinationFee.assetSymbol.normalizedXcmSymbol() == normalizedAssetSymbol) {
                    "Approved XCM destination fee asset must match route asset for ${key.originChainId} -> ${key.destinationChainId}"
                }
                require(executionSpec.destinationFee.mode != XcmDestinationFeeMode.ESTIMATED) {
                    "XCM estimated destination fee is unsupported until a production estimator is implemented"
                }
                require(destination.bridgeParachainId.normalizedOptionalIdentity() == null && executionSpec.bridge == null) {
                    "XCM bridge execution is unsupported until the production transfer engine consumes bridge fee semantics"
                }

                routes[key] = ApprovedXcmRoute(
                    key = key,
                    originIdentity = ApprovedXcmChainIdentity.from(origin),
                    destinationIdentity = ApprovedXcmChainIdentity.from(destinationChain),
                    originXcmChainId = originXcm.chainId.normalizedOptionalIdentity(),
                    xcmVersion = xcmVersion,
                    bridgeParachainId = destination.bridgeParachainId.normalizedOptionalIdentity(),
                    assetId = assetId,
                    minAmount = minAmount,
                    originAssetId = originAssetId,
                    originAssetPrecision = originAsset.precision,
                    executionSpec = executionSpec
                )
            }

            return ApprovedXcmRouteRegistry(routes)
        }
    }
}

object ApprovedXcmRouteRegistryLoader {
    private const val APPROVED_ROUTE_COLUMN_COUNT = 3

    fun load(bundledChainsJson: String, approvedRoutesTsv: String): ApprovedXcmRouteRegistry {
        require(bundledChainsJson.isNotBlank()) { "Bundled XCM chain registry must not be blank" }

        val chainType = object : TypeToken<List<ChainRemote?>>() {}.type
        val parsedRemoteChains = try {
            Gson().fromJson<List<ChainRemote?>?>(bundledChainsJson, chainType)
        } catch (error: RuntimeException) {
            throw IllegalArgumentException("Bundled XCM chain registry must be valid JSON", error)
        }
        require(!parsedRemoteChains.isNullOrEmpty()) { "Bundled XCM chain registry must contain chains" }
        require(parsedRemoteChains.none { it?.chainId.isNullOrBlank() }) {
            "Bundled XCM chain registry contains a blank chain id"
        }
        val remoteChains = parsedRemoteChains.filterNotNull()
        require(remoteChains.groupBy { it.chainId }.values.none { it.size != 1 }) {
            "Bundled XCM chain registry contains duplicate chain ids"
        }

        val routeKeys = parseApprovedRoutes(approvedRoutesTsv)
        val reviewedChains = try {
            remoteChains.map(ChainRemote::toChain)
        } catch (error: RuntimeException) {
            throw IllegalArgumentException("Bundled XCM chain registry contains malformed chain records", error)
        }
        return ApprovedXcmRouteRegistry.fromReviewedChains(
            reviewedChains = reviewedChains,
            routeKeys = routeKeys
        )
    }

    private fun parseApprovedRoutes(value: String): List<ApprovedXcmRouteKey> {
        require(value.isNotBlank()) { "Approved XCM route allowlist must not be blank" }

        val routes = mutableListOf<ApprovedXcmRouteKey>()
        value.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed

            val parts = line.split(Regex("\\s+"))
            require(parts.size == APPROVED_ROUTE_COLUMN_COUNT && parts.none(String::isBlank)) {
                "Invalid approved XCM route line ${index + 1}: expected origin destination asset"
            }
            routes += ApprovedXcmRouteKey(
                originChainId = parts[0],
                destinationChainId = parts[1],
                assetSymbol = parts[2]
            ).normalized()
        }

        require(routes.isNotEmpty()) { "Approved XCM route allowlist must contain at least one route" }
        require(routes.toSet().size == routes.size) { "Approved XCM route allowlist contains duplicate routes" }
        return routes
    }
}

internal data class ApprovedXcmRouteKey(
    val originChainId: String,
    val destinationChainId: String,
    val assetSymbol: String
) {
    fun normalized(): ApprovedXcmRouteKey {
        val origin = originChainId.normalizedRequiredIdentity()
        val destination = destinationChainId.normalizedRequiredIdentity()
        require(origin != destination) { "Approved XCM origin and destination chains must differ" }
        return copy(
            originChainId = origin,
            destinationChainId = destination,
            assetSymbol = assetSymbol.normalizedXcmSymbol()
        )
    }
}

internal data class EffectiveXcmRoute(
    val originChainId: String,
    val destinationChainId: String,
    val asset: XcmAsset,
    val executionSpec: XcmExecutionSpec,
    val originIdentity: XcmRouteChainIdentity,
    val destinationIdentity: XcmRouteChainIdentity
)

private data class ApprovedXcmRoute(
    val key: ApprovedXcmRouteKey,
    val originIdentity: ApprovedXcmChainIdentity,
    val destinationIdentity: ApprovedXcmChainIdentity,
    val originXcmChainId: String?,
    val xcmVersion: String,
    val bridgeParachainId: String?,
    val assetId: String,
    val minAmount: BigInteger?,
    val originAssetId: String,
    val originAssetPrecision: Int,
    val executionSpec: XcmExecutionSpec
) {
    fun resolve(chainsById: Map<String, List<Chain>>): EffectiveXcmRoute? {
        val origin = chainsById[key.originChainId]?.singleOrNull() ?: return null
        val destinationChain = chainsById[key.destinationChainId]?.singleOrNull() ?: return null
        val currentXcm = origin.xcm
        val matchesApprovedMetadata = originIdentity.matches(origin) &&
            destinationIdentity.matches(destinationChain) &&
            currentXcm != null &&
            matchesOriginMetadata(origin, currentXcm) &&
            matchesDestinationMetadata(currentXcm)

        return if (matchesApprovedMetadata) effectiveRoute() else null
    }

    private fun matchesOriginMetadata(origin: Chain, currentXcm: Chain.Xcm): Boolean {
        val currentOriginAsset = origin.assets.singleOrNull {
            runCatching { it.symbol.normalizedXcmSymbol() }.getOrNull() == key.assetSymbol
        }

        return currentXcm.chainId.normalizedOptionalIdentity() == originXcmChainId &&
            currentXcm.xcmVersion == xcmVersion &&
            currentOriginAsset != null &&
            currentOriginAsset.id.normalizedOptionalIdentity() == originAssetId &&
            currentOriginAsset.precision == originAssetPrecision
    }

    private fun matchesDestinationMetadata(currentXcm: Chain.Xcm): Boolean {
        val destination = currentXcm.availableDestinations.orEmpty().singleOrNull {
            it.chainId.normalizedOptionalIdentity() == key.destinationChainId
        } ?: return false
        if (destination.bridgeParachainId.normalizedOptionalIdentity() != bridgeParachainId) return false

        val routeAsset = destination.assets.orEmpty().singleOrNull {
            runCatching { it.symbol.normalizedXcmSymbol() }.getOrNull() == key.assetSymbol
        } ?: return false

        return routeAsset.id.normalizedOptionalIdentity() == assetId &&
            runCatching { parseOptionalMinAmount(routeAsset.minAmount) }
                .fold(
                    onSuccess = { it == minAmount },
                    onFailure = { false }
                )
    }

    private fun effectiveRoute(): EffectiveXcmRoute = EffectiveXcmRoute(
        originChainId = key.originChainId,
        destinationChainId = key.destinationChainId,
        asset = XcmAsset(
            id = assetId,
            symbol = key.assetSymbol,
            minAmount = minAmount,
            originChainId = key.originChainId,
            originAssetId = originAssetId,
            originAssetPrecision = originAssetPrecision
        ),
        executionSpec = executionSpec,
        originIdentity = originIdentity.toRouteIdentity(),
        destinationIdentity = destinationIdentity.toRouteIdentity()
    )
}

private data class ApprovedXcmChainIdentity(
    val chainId: String,
    val parentId: String?,
    val paraId: String?,
    val addressPrefix: Int,
    val ecosystem: jp.co.soramitsu.core.models.Ecosystem,
    val isEthereumBased: Boolean,
    val isEthereumChain: Boolean,
    val isTestNet: Boolean
) {
    fun matches(chain: Chain): Boolean {
        return chain.id == chainId &&
            chain.parentId.normalizedOptionalIdentity() == parentId &&
            chain.paraId.normalizedOptionalIdentity() == paraId &&
            chain.addressPrefix == addressPrefix &&
            chain.ecosystem == ecosystem &&
            chain.isEthereumBased == isEthereumBased &&
            chain.isEthereumChain == isEthereumChain &&
            chain.isTestNet == isTestNet
    }

    fun toRouteIdentity(): XcmRouteChainIdentity = XcmRouteChainIdentity(
        chainId = chainId,
        parentId = parentId,
        paraId = paraId,
        addressPrefix = addressPrefix,
        ecosystem = ecosystem,
        isEthereumBased = isEthereumBased,
        isEthereumChain = isEthereumChain,
        isTestNet = isTestNet
    )

    companion object {
        fun from(chain: Chain): ApprovedXcmChainIdentity = ApprovedXcmChainIdentity(
            chainId = chain.id.normalizedRequiredIdentity(),
            parentId = chain.parentId.normalizedOptionalIdentity(),
            paraId = chain.paraId.normalizedOptionalIdentity(),
            addressPrefix = chain.addressPrefix,
            ecosystem = chain.ecosystem,
            isEthereumBased = chain.isEthereumBased,
            isEthereumChain = chain.isEthereumChain,
            isTestNet = chain.isTestNet
        )
    }
}

private fun requireUnambiguousPerAssetExecution(chain: Chain) {
    val xcm = chain.xcm ?: return
    val originChainId = chain.id.normalizedRequiredIdentity()

    xcm.availableDestinations.orEmpty().forEach { destination ->
        requireUnambiguousDestinationExecution(originChainId, xcm.xcmVersion, destination)
    }
}

@Suppress("DEPRECATION")
private fun requireUnambiguousDestinationExecution(
    originChainId: String,
    xcmVersion: String?,
    destination: Chain.Xcm.Destination
) {
    val destinationChainId = destination.chainId.normalizedRequiredIdentity()
    require(destination.execution == null) {
        "Legacy destination-scoped XCM execution is forbidden for $originChainId -> $destinationChainId"
    }

    val assets = destination.assets.orEmpty()
    val normalizedSymbols = assets.map { it.symbol.normalizedXcmSymbol() }
    require(normalizedSymbols.toSet().size == normalizedSymbols.size) {
        "XCM destination contains duplicate or ambiguous route assets for $originChainId -> $destinationChainId"
    }

    assets.filter { it.execution != null }.forEach { asset ->
        XcmExecutionSpecValidator.requireValid(
            originChainId = originChainId,
            destinationChainId = destinationChainId,
            assetSymbol = asset.symbol.normalizedXcmSymbol(),
            xcmVersion = xcmVersion,
            destination = destination,
            asset = asset
        )
    }
}

private fun String?.normalizedRequiredIdentity(): String {
    val raw = this.orEmpty()
    require(raw == raw.trim()) { "Approved XCM identity value must be canonical without surrounding whitespace" }
    val value = raw
    require(value.isNotEmpty()) { "Approved XCM identity value must not be blank" }
    return value
}

private fun String?.normalizedOptionalIdentity(): String? {
    val raw = this ?: return null
    require(raw == raw.trim()) { "Approved XCM identity value must be canonical without surrounding whitespace" }
    return raw.takeIf(String::isNotEmpty)
}

private fun String?.normalizedXcmSymbol(): String {
    val symbol = this?.trim().orEmpty()
        .replace(Regex("^xc", RegexOption.IGNORE_CASE), "")
        .uppercase(Locale.US)
    require(symbol.isNotEmpty()) { "Approved XCM asset symbol must not be blank" }
    return symbol
}

private fun parseOptionalMinAmount(value: String?): BigInteger? {
    if (value == null) return null
    require(value == value.trim()) { "Approved XCM minAmount must not contain surrounding whitespace" }
    val normalized = value
    require(Regex("^(0|[1-9][0-9]*)$").matches(normalized)) {
        "Approved XCM minAmount must be a non-negative integer string"
    }
    return normalized.toBigInteger()
}

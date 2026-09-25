package jp.co.soramitsu.wallet.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.FixedArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromByteArrayOrNull
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Null
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.DynamicByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.skipAliases
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageOrNull
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.xcm.ReviewedBridgeAssetKind
import jp.co.soramitsu.xcm.ReviewedBridgeDestinationMinimum
import jp.co.soramitsu.xcm.ReviewedBridgeExecutionKind
import jp.co.soramitsu.xcm.ReviewedBridgeExternalAsset
import jp.co.soramitsu.xcm.ReviewedBridgeRecipientKind
import jp.co.soramitsu.xcm.ReviewedBridgeRegistrationAuthority
import jp.co.soramitsu.xcm.ReviewedBridgeRoute
import jp.co.soramitsu.xcm.ReviewedBridgeRuntimeIdentity
import jp.co.soramitsu.xcm.ReviewedBridgeRuntimeIdentityResolver
import jp.co.soramitsu.xcm.ReviewedBridgeRuntimeMinimum
import jp.co.soramitsu.xcm.ReviewedPolkaswapBridgeCatalog

/**
 * Live storage authority matching web `reviewedRuntimeAuthority.ts`. Every lookup is keyed by the
 * immutable reviewed route and decoded through the connected runtime metadata.
 */
class ReviewedPolkaswapBridgeRuntimeAuthority(
    private val chainRegistry: ChainRegistry,
    private val remoteStorage: StorageDataSource,
    private val rpcCalls: RpcCalls
) : ReviewedBridgeRegistrationAuthority, ReviewedBridgeRuntimeIdentityResolver {

    override suspend fun resolve(chainId: String): ReviewedBridgeRuntimeIdentity {
        val identity = rpcCalls.getRuntimeIdentity(chainId)
        return ReviewedBridgeRuntimeIdentity(
            genesisHash = identity.genesisHash,
            specName = identity.specName,
            specVersion = identity.specVersion,
            transactionVersion = identity.transactionVersion
        )
    }

    override suspend fun verify(route: ReviewedBridgeRoute): String {
        val proof = when (route.execution.kind) {
            ReviewedBridgeExecutionKind.SoraBridgeProxyBurnV3 -> {
                if (route.execution.bridgeNetwork.name == "Liberland") {
                    assertLiberlandRegistration(route, ReviewedPolkaswapBridgeCatalog.SORA_CHAIN_ID)
                } else {
                    assertParachainRegistration(route, ReviewedPolkaswapBridgeCatalog.SORA_CHAIN_ID)
                }
            }

            ReviewedBridgeExecutionKind.LiberlandToSoraBurn ->
                assertLiberlandRegistration(route, ReviewedPolkaswapBridgeCatalog.SORA_CHAIN_ID)

            ReviewedBridgeExecutionKind.ExternalToSoraXcmV3 ->
                assertParachainRegistration(route, ReviewedPolkaswapBridgeCatalog.SORA_CHAIN_ID)
        }

        return proofFingerprint("registration", route.routeId, *proof.toTypedArray())
    }

    suspend fun destinationMinimum(route: ReviewedBridgeRoute): ReviewedBridgeRuntimeMinimum {
        val minimum = route.execution.destinationMinimum
            ?: return ReviewedBridgeRuntimeMinimum(
                BigInteger.ZERO,
                proofFingerprint("minimum", route.routeId, "none")
            )

        val (destinationPlanks, proofParts) = when (minimum) {
            is ReviewedBridgeDestinationMinimum.BalancesExistentialDeposit -> {
                val runtime = chainRegistry.getRuntime(route.destinationChainId)
                val module = runtime.metadata.moduleOrNull("Balances")
                    ?: throw IllegalStateException("cross_chain_runtime_minimum_unavailable")
                val constant = module.constants["ExistentialDeposit"]
                    ?: throw IllegalStateException("cross_chain_runtime_minimum_unavailable")
                val value = constant.type?.fromByteArrayOrNull(runtime, constant.value).asStrictNumber()
                value to listOf(
                    route.destinationChainId,
                    "Balances.ExistentialDeposit",
                    value.toString()
                )
            }

            is ReviewedBridgeDestinationMinimum.AssetsMinBalance -> {
                val details = readStorage(
                    route.destinationChainId,
                    "Assets",
                    "Asset",
                    listOf(BigInteger.valueOf(minimum.assetId))
                ).asStrictStruct()
                val value = (details.mapping["minBalance"] ?: details.mapping["min_balance"])
                    .asStrictNumber()
                value to listOf(
                    route.destinationChainId,
                    "Assets.Asset",
                    minimum.assetId.toString(),
                    value.toString()
                )
            }

            is ReviewedBridgeDestinationMinimum.AcalaTokenMinimum -> {
                val currencyId = mapOf("NativeAssetId" to mapOf("Token" to minimum.token))
                val details = readStorage(
                    route.destinationChainId,
                    "AssetRegistry",
                    "AssetMetadatas",
                    listOf(currencyId)
                ).asStrictStruct()
                val value = (
                    details.mapping["minimalBalance"] ?: details.mapping["minimal_balance"] ?:
                        details.mapping["existentialDeposit"] ?: details.mapping["existential_deposit"]
                    )
                    .asStrictNumber()
                value to listOf(
                    route.destinationChainId,
                    "AssetRegistry.AssetMetadatas",
                    "NativeAssetId.Token.${minimum.token}",
                    value.toString()
                )
            }

            is ReviewedBridgeDestinationMinimum.SoraParachainAssetMinimum -> {
                val identity = resolve(minimum.chainId)
                require(identity.genesisHash.matchesChainId(minimum.chainId)) {
                    "cross_chain_runtime_genesis_mismatch"
                }
                val multilocation = readStorage(
                    minimum.chainId,
                    "XcmApp",
                    "AssetIdToMultilocation",
                    listOf(minimum.assetId)
                )
                val value = readStorage(
                    minimum.chainId,
                    "XcmApp",
                    "AssetMinimumAmount",
                    listOf(multilocation)
                ).asStrictNumber()
                value to listOf(
                    minimum.chainId,
                    identity.toString(),
                    "XcmApp.AssetIdToMultilocation",
                    minimum.assetId.lowercase(),
                    "XcmApp.AssetMinimumAmount",
                    value.toString()
                )
            }
        }
        require(destinationPlanks.signum() > 0) { "cross_chain_runtime_minimum_unavailable" }
        val originPlanks = BigDecimal(destinationPlanks, minimum.precision)
            .movePointRight(route.precision)
            .toBigIntegerExact()
        return ReviewedBridgeRuntimeMinimum(
            amountInOriginPlanks = originPlanks,
            proofFingerprint = proofFingerprint("minimum", route.routeId, *proofParts.toTypedArray())
        )
    }

    private suspend fun assertParachainRegistration(
        route: ReviewedBridgeRoute,
        authorityChainId: String
    ): List<String> {
        val execution = route.execution
        val network = execution.bridgeNetwork.name
        val assetId = execution.soraAssetId
        val bridgeParachainId = execution.bridgeParachainId
        val routeProof = when {
            bridgeParachainId != null -> {
                val allowed = readStorage(
                    authorityChainId,
                    "ParachainBridgeApp",
                    "AllowedParachainAssets",
                    listOf(network, BigInteger.valueOf(bridgeParachainId.toLong()))
                ).asStrictList().map { it.asAssetIdHex() }
                require(assetId.lowercase() in allowed) { "cross_chain_runtime_registration_drift" }
                listOf("allowedParachainAssets", bridgeParachainId.toString(), allowed.joinToString(","))
            }

            execution.recipientKind == ReviewedBridgeRecipientKind.Parachain -> {
                val paraId = requireNotNull(execution.destinationParaId)
                val allowed = readStorage(
                    authorityChainId,
                    "ParachainBridgeApp",
                    "AllowedParachainAssets",
                    listOf(network, BigInteger.valueOf(paraId.toLong()))
                ).asStrictList().map { it.asAssetIdHex() }
                require(assetId.lowercase() in allowed) { "cross_chain_runtime_registration_drift" }
                listOf("allowedParachainAssets", paraId.toString(), allowed.joinToString(","))
            }

            else -> {
                val registered = readStorage(
                    authorityChainId,
                    "ParachainBridgeApp",
                    "RelaychainAsset",
                    listOf(network)
                ).asAssetIdHex()
                require(registered == assetId.lowercase()) { "cross_chain_runtime_registration_drift" }
                listOf("relaychainAsset", registered)
            }
        }
        val kind = readStorage(
            authorityChainId,
            "ParachainBridgeApp",
            "AssetKinds",
            listOf(network, assetId)
        ).asEnumName()
        val expectedKind = execution.soraAssetKind.name
        require(kind.equals(expectedKind, ignoreCase = true)) { "cross_chain_runtime_registration_drift" }
        val precision = readStorage(
            authorityChainId,
            "ParachainBridgeApp",
            "SidechainPrecision",
            listOf(network, assetId)
        ).asStrictNumber().intValueExact()
        val expectedPrecision = if (execution.kind == ReviewedBridgeExecutionKind.ExternalToSoraXcmV3) {
            route.precision
        } else {
            execution.sidechainPrecision
        }
        require(precision == expectedPrecision) { "cross_chain_runtime_registration_drift" }
        return listOf(
            authorityChainId,
            "ParachainBridgeApp",
            network,
            assetId.lowercase(),
            *routeProof.toTypedArray(),
            "assetKind=$kind",
            "sidechainPrecision=$precision"
        )
    }

    private suspend fun assertLiberlandRegistration(
        route: ReviewedBridgeRoute,
        authorityChainId: String
    ): List<String> {
        val execution = route.execution
        val assetId = execution.soraAssetId
        val kind = readStorage(
            authorityChainId,
            "SubstrateBridgeApp",
            "AssetKinds",
            listOf("Liberland", assetId)
        ).asEnumName()
        require(kind.equals(execution.soraAssetKind.name, ignoreCase = true)) {
            "cross_chain_runtime_registration_drift"
        }
        val precision = readStorage(
            authorityChainId,
            "SubstrateBridgeApp",
            "SidechainPrecision",
            listOf("Liberland", assetId)
        ).asStrictNumber().intValueExact()
        require(precision == execution.sidechainPrecision) { "cross_chain_runtime_registration_drift" }
        val external = readStorage(
            authorityChainId,
            "SubstrateBridgeApp",
            "SidechainAssetId",
            listOf("Liberland", assetId)
        )
        require(external.matchesExternalAsset(requireNotNull(execution.externalAsset))) {
            "cross_chain_runtime_registration_drift"
        }
        return listOf(
            authorityChainId,
            "SubstrateBridgeApp",
            "Liberland",
            assetId.lowercase(),
            "assetKind=$kind",
            "sidechainPrecision=$precision",
            "sidechainAssetId=${external.normalizedProofValue()}"
        )
    }

    private suspend fun readStorage(
        chainId: String,
        moduleName: String,
        storageName: String,
        logicalArguments: List<Any?>
    ): Any = remoteStorage.query(
        chainId = chainId,
        keyBuilder = { runtime ->
            val entry = runtime.metadata.moduleOrNull(moduleName)?.storageOrNull(storageName)
                ?: throw IllegalStateException("cross_chain_runtime_capability_missing")
            val keyTypes = entry.type.let { it as? jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntryType.NMap }
                ?.keys ?: emptyList()
            require(keyTypes.size == logicalArguments.size) { "cross_chain_runtime_registration_drift" }
            val encodedArguments = keyTypes.zip(logicalArguments).map { (type, logical) ->
                requireNotNull(type).reviewedRuntimeValue(logical)
            }
            entry.storageKey(runtime, *encodedArguments.toTypedArray())
        },
        binding = { scale, runtime ->
            val encoded = scale ?: throw IllegalStateException("cross_chain_runtime_registration_missing")
            val entry = runtime.metadata.moduleOrNull(moduleName)?.storageOrNull(storageName)
                ?: throw IllegalStateException("cross_chain_runtime_capability_missing")
            entry.returnType().fromHexOrNull(runtime, encoded)
                ?: throw IllegalStateException("cross_chain_runtime_registration_drift")
        }
    )

    private fun Type<*>.reviewedRuntimeValue(logical: Any?): Any? {
        val type = skipAliases() ?: throw IllegalStateException("cross_chain_runtime_capability_missing")
        if (type.isValidInstance(logical)) return logical

        if (logical is Number) {
            val number = logical.toString().toBigInteger()
            if (type.isValidInstance(number)) return number
        }

        if (logical is String && logical.startsWith("0x")) {
            val bytes = logical.fromHex()
            when (type) {
                is FixedByteArray -> if (type.isValidInstance(bytes)) return bytes
                is DynamicByteArray -> return bytes
                is FixedArray -> {
                    val values = bytes.map { byte ->
                        val unsigned = byte.toUByte().toLong().toBigInteger()
                        requireNotNull(type.innerType).reviewedRuntimeValue(unsigned)
                    }
                    if (type.isValidInstance(values)) return values
                }
            }
        }

        if (type is DictEnum && logical is String) {
            val childType = type[logical] ?: Null
            return DictEnum.Entry(logical, childType.reviewedRuntimeValue(null))
        }

        if (type is DictEnum && logical is Map<*, *> && logical.size == 1) {
            val (variant, value) = logical.entries.single()
            val name = variant as? String
                ?: throw IllegalStateException("cross_chain_runtime_registration_drift")
            val childType = type[name] ?: Null
            return DictEnum.Entry(name, childType.reviewedRuntimeValue(value))
        }

        if (type is Struct && logical !is Map<*, *> && type.mapping.size == 1) {
            val (field, reference) = type.mapping.entries.single()
            return Struct.Instance(
                mapOf(field to requireNotNull(reference.value).reviewedRuntimeValue(logical))
            )
        }

        if (type is Struct && logical is Map<*, *>) {
            val values = type.mapping.mapValues { (field, reference) ->
                val reviewedValue = when {
                    logical.containsKey(field) -> logical[field]
                    logical.containsKey(field.toReviewedSnakeCase()) -> logical[field.toReviewedSnakeCase()]
                    else -> throw IllegalStateException("cross_chain_runtime_registration_drift")
                }
                requireNotNull(reference.value).reviewedRuntimeValue(reviewedValue)
            }
            return Struct.Instance(values)
        }

        if (type is Tuple && logical is List<*> && logical.size == type.typeReferences.size) {
            return logical.mapIndexed { index, value ->
                requireNotNull(type[index]).reviewedRuntimeValue(value)
            }
        }

        throw IllegalStateException("cross_chain_runtime_registration_drift")
    }

    private fun Any?.asStrictStruct(): Struct.Instance = this as? Struct.Instance
        ?: throw IllegalStateException("cross_chain_runtime_registration_drift")

    private fun Any?.asStrictList(): List<*> = this as? List<*>
        ?: throw IllegalStateException("cross_chain_runtime_registration_drift")

    private fun Any?.asStrictNumber(): BigInteger = this as? BigInteger
        ?: throw IllegalStateException("cross_chain_runtime_registration_drift")

    private fun Any?.asEnumName(): String = when (this) {
        is String -> this
        is DictEnum.Entry<*> -> name
        else -> throw IllegalStateException("cross_chain_runtime_registration_drift")
    }

    private fun Any?.asAssetIdHex(): String = when (this) {
        is ByteArray -> toHexString(withPrefix = true).lowercase()
        is String -> takeIf { ASSET_ID.matches(it) }?.lowercase()
        is Struct.Instance -> mapping.values.singleOrNull().asAssetIdHex()
        is DictEnum.Entry<*> -> value.asAssetIdHex()
        is List<*> -> map {
            val value = it.asStrictNumber()
            require(value in BigInteger.ZERO..BigInteger.valueOf(255)) {
                "cross_chain_runtime_registration_drift"
            }
            value.toInt().toByte()
        }.toByteArray().toHexString(withPrefix = true).lowercase()
        else -> null
    } ?: throw IllegalStateException("cross_chain_runtime_registration_drift")

    private fun Any?.matchesExternalAsset(expected: ReviewedBridgeExternalAsset): Boolean = when (expected) {
        ReviewedBridgeExternalAsset.Lld -> when (this) {
            is String -> equals("LLD", ignoreCase = true)
            is DictEnum.Entry<*> -> name.equals("LLD", ignoreCase = true) && value == null
            else -> false
        }

        is ReviewedBridgeExternalAsset.Asset -> when (this) {
            is BigInteger -> this == BigInteger.valueOf(expected.id)
            is DictEnum.Entry<*> -> name.equals("Asset", ignoreCase = true) &&
                value.asStrictNumber() == BigInteger.valueOf(expected.id)
            else -> false
        }
    }

    private fun Any?.normalizedProofValue(): String = when (this) {
        is ByteArray -> toHexString(withPrefix = true).lowercase()
        is BigInteger, is String -> toString()
        is DictEnum.Entry<*> -> "$name(${value.normalizedProofValue()})"
        is Struct.Instance -> mapping.entries.joinToString(",", "{", "}") { (key, value) ->
            "$key=${value.normalizedProofValue()}"
        }
        is List<*> -> joinToString(",", "[", "]") { it.normalizedProofValue() }
        null -> "null"
        else -> throw IllegalStateException("cross_chain_runtime_registration_drift")
    }

    private fun proofFingerprint(kind: String, routeId: String, vararg parts: String): String =
        (listOf("1", kind, routeId) + parts).joinToString("\u001f")
            .toByteArray().blake2b256().toHexString(withPrefix = true)

    private fun String.matchesChainId(chainId: String): Boolean =
        removePrefix("0x").equals(chainId.removePrefix("0x"), ignoreCase = true)

    private fun String.toReviewedSnakeCase(): String = buildString(length + 4) {
        this@toReviewedSnakeCase.forEachIndexed { index, character ->
            if (character.isUpperCase()) {
                if (index != 0) append('_')
                append(character.lowercaseChar())
            } else {
                append(character)
            }
        }
    }

    private companion object {
        val ASSET_ID = Regex("^0x[0-9a-fA-F]{64}$")
    }
}

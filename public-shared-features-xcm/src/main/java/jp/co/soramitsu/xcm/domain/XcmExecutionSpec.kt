package jp.co.soramitsu.xcm.domain

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigInteger
import java.util.Locale

data class XcmExecutionSpec(
    val palletName: String,
    val callName: String,
    val transferType: XcmTransferType,
    val argumentShape: XcmArgumentShape,
    val xcmVersion: String,
    val destinationLocation: XcmMultiLocationSpec,
    val assetLocation: XcmMultiLocationSpec,
    val beneficiaryLocation: XcmMultiLocationSpec,
    val feeAssetLocation: XcmMultiLocationSpec,
    val feeAssetItem: Int,
    val weightLimit: XcmWeightLimitSpec,
    val destinationFee: XcmDestinationFeeSpec,
    val bridge: XcmBridgeExecutionSpec?
)

enum class XcmTransferType {
    RESERVE_TRANSFER_ASSETS,
    LIMITED_RESERVE_TRANSFER_ASSETS,
    TELEPORT_ASSETS,
    LIMITED_TELEPORT_ASSETS,
    X_TOKENS_TRANSFER_MULTIASSET
}

enum class XcmArgumentShape {
    POLKADOT_XCM_TRANSFER_ASSETS,
    X_TOKENS_TRANSFER_MULTIASSET
}

data class XcmMultiLocationSpec(
    val parents: Int,
    val interior: String,
    val junctions: List<XcmJunctionSpec>
)

data class XcmJunctionSpec(
    val type: XcmJunctionType,
    val value: String?
)

enum class XcmJunctionType {
    PARACHAIN,
    ACCOUNT_ID32,
    ACCOUNT_KEY20,
    PALLET_INSTANCE,
    GENERAL_INDEX,
    GENERAL_KEY
}

data class XcmWeightLimitSpec(
    val type: XcmWeightLimitType,
    val refTime: BigInteger?,
    val proofSize: BigInteger?
)

enum class XcmWeightLimitType {
    UNLIMITED,
    LIMITED
}

data class XcmDestinationFeeSpec(
    val mode: XcmDestinationFeeMode,
    val assetSymbol: String,
    val amount: BigInteger?
)

enum class XcmDestinationFeeMode {
    INCLUDED,
    ESTIMATED,
    FIXED
}

data class XcmBridgeExecutionSpec(
    val parachainId: String,
    val feeAssetLocation: XcmMultiLocationSpec,
    val feeAssetItem: Int
)

internal object XcmExecutionSpecValidator {

    fun requireValid(
        originChainId: ChainId,
        destinationChainId: ChainId,
        assetSymbol: String,
        xcmVersion: String?,
        destination: Chain.Xcm.Destination,
        asset: Chain.Xcm.Asset
    ): XcmExecutionSpec {
        require(destination.execution == null) {
            "Legacy destination-scoped XCM execution is ambiguous for $assetSymbol from $originChainId to $destinationChainId"
        }
        val normalizedAssetSymbol = assetSymbol.normalizedRouteAssetSymbol()
        require(asset.symbol.normalizedRouteAssetSymbol() == normalizedAssetSymbol) {
            "XCM execution spec asset does not match $assetSymbol from $originChainId to $destinationChainId"
        }
        val execution = asset.execution ?: throw IllegalArgumentException(
            "XCM execution spec missing for $assetSymbol from $originChainId to $destinationChainId"
        )

        val routeBridgeParachainId = destination.bridgeParachainId?.trim().orEmpty()
        require(routeBridgeParachainId.isEmpty() && execution.bridge == null) {
            "XCM bridge execution is unsupported until the production transfer engine consumes bridge fee semantics"
        }
        val bridgeSpec: XcmBridgeExecutionSpec? = null

        val palletName = execution.palletName.requiredField("palletName")
        val callName = execution.callName.requiredField("callName")
        val transferType = execution.transferType.requiredTransferType()
        val argumentShape = execution.argumentShape.optionalArgumentShape()
        requireValidCallShape(palletName, callName, transferType, argumentShape)
        val normalizedXcmVersion = xcmVersion.requiredField("xcmVersion")
        require(xcmVersion == normalizedXcmVersion) {
            "XCM execution spec xcmVersion must not contain surrounding whitespace"
        }
        require(Regex("^v[1-9][0-9]*$").matches(normalizedXcmVersion)) {
            "XCM execution spec xcmVersion must use canonical v<number> syntax"
        }
        val destinationLocation = execution.destinationLocation.requiredMultiLocation("destinationLocation")
            .requireNoRecipientAccount("destinationLocation")
        val assetLocation = execution.assetLocation.requiredMultiLocation("assetLocation")
            .requireNoRecipientAccount("assetLocation")
        val beneficiaryLocation = execution.beneficiaryLocation.requiredMultiLocation("beneficiaryLocation")
            .requireExactlyOneRecipientAccount()
        val feeAssetLocation = execution.feeAssetLocation.requiredMultiLocation("feeAssetLocation")
            .requireNoRecipientAccount("feeAssetLocation")
        require(feeAssetLocation == assetLocation) {
            "XCM single-asset execution feeAssetLocation must equal assetLocation"
        }
        val feeAssetItem = execution.feeAssetItem.requiredNonNegative("feeAssetItem")
        require(feeAssetItem == 0) {
            "XCM single-asset execution feeAssetItem must be zero"
        }

        val destinationFee = execution.destinationFee.requiredDestinationFee()
        require(destinationFee.assetSymbol.normalizedRouteAssetSymbol() == normalizedAssetSymbol) {
            "XCM destination fee asset must match exact route asset $normalizedAssetSymbol"
        }

        return XcmExecutionSpec(
            palletName = palletName,
            callName = callName,
            transferType = transferType,
            argumentShape = argumentShape,
            xcmVersion = normalizedXcmVersion,
            destinationLocation = destinationLocation,
            assetLocation = assetLocation,
            beneficiaryLocation = beneficiaryLocation,
            feeAssetLocation = feeAssetLocation,
            feeAssetItem = feeAssetItem,
            weightLimit = execution.weightLimit.requiredWeightLimit(),
            destinationFee = destinationFee,
            bridge = bridgeSpec
        )
    }

    private fun String?.normalizedRouteAssetSymbol(): String {
        val normalized = this?.trim().orEmpty()
            .replace(Regex("^xc", RegexOption.IGNORE_CASE), "")
            .uppercase(Locale.US)
        require(normalized.isNotEmpty()) { "XCM execution spec route asset symbol must not be blank" }
        return normalized
    }

    private fun String?.requiredField(fieldName: String): String {
        val value = this?.trim().orEmpty()
        require(value.isNotEmpty()) { "XCM execution spec field $fieldName must not be blank" }
        return value
    }

    private fun Int?.requiredNonNegative(fieldName: String): Int {
        val value = requireNotNull(this) { "XCM execution spec field $fieldName is required" }
        require(value >= 0) { "XCM execution spec field $fieldName must not be negative" }
        return value
    }

    private fun String?.requiredTransferType(): XcmTransferType {
        return when (requiredField("transferType").normalizedEnumValue()) {
            "RESERVE_TRANSFER_ASSETS" -> XcmTransferType.RESERVE_TRANSFER_ASSETS
            "LIMITED_RESERVE_TRANSFER_ASSETS" -> XcmTransferType.LIMITED_RESERVE_TRANSFER_ASSETS
            "TELEPORT_ASSETS" -> XcmTransferType.TELEPORT_ASSETS
            "LIMITED_TELEPORT_ASSETS" -> XcmTransferType.LIMITED_TELEPORT_ASSETS
            "X_TOKENS_TRANSFER_MULTIASSET" -> XcmTransferType.X_TOKENS_TRANSFER_MULTIASSET
            else -> throw IllegalArgumentException("XCM execution spec transferType is unsupported")
        }
    }

    private fun String?.optionalArgumentShape(): XcmArgumentShape {
        return when (this?.trim().orEmpty().takeIf(String::isNotEmpty)?.normalizedEnumValue()) {
            null -> XcmArgumentShape.POLKADOT_XCM_TRANSFER_ASSETS
            "POLKADOT_XCM_TRANSFER_ASSETS" -> XcmArgumentShape.POLKADOT_XCM_TRANSFER_ASSETS
            "X_TOKENS_TRANSFER_MULTIASSET" -> XcmArgumentShape.X_TOKENS_TRANSFER_MULTIASSET
            else -> throw IllegalArgumentException("XCM execution spec argumentShape is unsupported")
        }
    }

    private fun requireValidCallShape(
        palletName: String,
        callName: String,
        transferType: XcmTransferType,
        argumentShape: XcmArgumentShape
    ) {
        when (argumentShape) {
            XcmArgumentShape.POLKADOT_XCM_TRANSFER_ASSETS -> {
                require(palletName == "PolkadotXcm") {
                    "XCM PolkadotXcm transfer-assets argument shape requires palletName PolkadotXcm"
                }
                require(callName == transferType.polkadotXcmCallName()) {
                    "XCM PolkadotXcm transfer-assets argument shape callName must match transferType"
                }
            }
            XcmArgumentShape.X_TOKENS_TRANSFER_MULTIASSET -> {
                require(palletName == "XTokens") {
                    "XCM XTokens transferMultiasset argument shape requires palletName XTokens"
                }
                require(callName == "transferMultiasset") {
                    "XCM XTokens transferMultiasset argument shape requires callName transferMultiasset"
                }
                require(transferType == XcmTransferType.X_TOKENS_TRANSFER_MULTIASSET) {
                    "XCM XTokens transferMultiasset argument shape requires transferType xTokensTransferMultiasset"
                }
            }
        }
    }

    private fun XcmTransferType.polkadotXcmCallName(): String {
        return when (this) {
            XcmTransferType.RESERVE_TRANSFER_ASSETS -> "reserveTransferAssets"
            XcmTransferType.LIMITED_RESERVE_TRANSFER_ASSETS -> "limitedReserveTransferAssets"
            XcmTransferType.TELEPORT_ASSETS -> "teleportAssets"
            XcmTransferType.LIMITED_TELEPORT_ASSETS -> "limitedTeleportAssets"
            XcmTransferType.X_TOKENS_TRANSFER_MULTIASSET -> ""
        }
    }

    private fun Chain.Xcm.MultiLocation?.requiredMultiLocation(fieldName: String): XcmMultiLocationSpec {
        val location = requireNotNull(this) { "XCM execution spec field $fieldName is required" }
        val parents = location.parents.requiredNonNegative("$fieldName.parents")
        val interior = location.interior.requiredField("$fieldName.interior")
        return XcmMultiLocationSpec(
            parents = parents,
            interior = interior,
            junctions = XcmMultiLocationParser.requireValidInterior(interior, "$fieldName.interior")
        )
    }

    private fun XcmMultiLocationSpec.requireNoRecipientAccount(fieldName: String): XcmMultiLocationSpec {
        require(junctions.none { it.isRecipientAccount() }) {
            "XCM execution spec field $fieldName must not contain a recipient account junction"
        }
        return this
    }

    private fun XcmMultiLocationSpec.requireExactlyOneRecipientAccount(): XcmMultiLocationSpec {
        require(junctions.count { it.isRecipientAccount() } == 1) {
            "XCM execution spec beneficiaryLocation must contain exactly one recipient account junction"
        }
        return this
    }

    private fun XcmJunctionSpec.isRecipientAccount(): Boolean =
        type == XcmJunctionType.ACCOUNT_ID32 || type == XcmJunctionType.ACCOUNT_KEY20

    private fun Chain.Xcm.WeightLimit?.requiredWeightLimit(): XcmWeightLimitSpec {
        val weightLimit = requireNotNull(this) { "XCM execution spec field weightLimit is required" }
        val type = when (weightLimit.type.requiredField("weightLimit.type").normalizedEnumValue()) {
            "UNLIMITED" -> XcmWeightLimitType.UNLIMITED
            "LIMITED" -> XcmWeightLimitType.LIMITED
            else -> throw IllegalArgumentException("XCM execution spec weightLimit.type is unsupported")
        }

        val refTime = weightLimit.refTime?.parseUnsignedBigInteger("weightLimit.refTime")
        val proofSize = weightLimit.proofSize?.parseUnsignedBigInteger("weightLimit.proofSize")
        require(type == XcmWeightLimitType.UNLIMITED || refTime != null && proofSize != null) {
            "XCM limited weightLimit requires refTime and proofSize"
        }
        require(type == XcmWeightLimitType.LIMITED || refTime == null && proofSize == null) {
            "XCM unlimited weightLimit must not include refTime or proofSize"
        }

        return XcmWeightLimitSpec(type = type, refTime = refTime, proofSize = proofSize)
    }

    private fun Chain.Xcm.DestinationFee?.requiredDestinationFee(): XcmDestinationFeeSpec {
        val destinationFee = requireNotNull(this) { "XCM execution spec field destinationFee is required" }
        val mode = when (destinationFee.mode.requiredField("destinationFee.mode").normalizedEnumValue()) {
            "INCLUDED" -> XcmDestinationFeeMode.INCLUDED
            "ESTIMATED" -> XcmDestinationFeeMode.ESTIMATED
            "FIXED" -> XcmDestinationFeeMode.FIXED
            else -> throw IllegalArgumentException("XCM execution spec destinationFee.mode is unsupported")
        }
        require(mode != XcmDestinationFeeMode.ESTIMATED) {
            "XCM estimated destination fee is unsupported until a production estimator is implemented"
        }
        val amount = destinationFee.amount?.parseUnsignedBigInteger("destinationFee.amount")
        require(mode == XcmDestinationFeeMode.FIXED || amount == null) {
            "XCM destination fee amount is only valid for fixed destination fees"
        }
        require(mode != XcmDestinationFeeMode.FIXED || amount != null) {
            "XCM fixed destination fee requires amount"
        }

        return XcmDestinationFeeSpec(
            mode = mode,
            assetSymbol = destinationFee.assetSymbol.requiredField("destinationFee.assetSymbol"),
            amount = amount
        )
    }

    private fun String.parseUnsignedBigInteger(fieldName: String): BigInteger {
        val value = trim().toBigIntegerOrNull()
        require(value != null && value >= BigInteger.ZERO) {
            "XCM execution spec field $fieldName must be a non-negative integer"
        }
        return value
    }

    private fun String.normalizedEnumValue(): String {
        return trim()
            .replace('-', '_')
            .replace(' ', '_')
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .uppercase(Locale.US)
    }
}

internal object XcmMultiLocationParser {
    private const val ACCOUNT_PLACEHOLDER = "<account>"

    private val xJunctionRegex = Regex("^X([1-8])\\((.*)\\)$")
    private val junctionRegex = Regex("^([A-Za-z][A-Za-z0-9]*)(?:\\((.*)\\))?$")

    fun requireValidInterior(interior: String, fieldName: String): List<XcmJunctionSpec> {
        val value = interior.trim()
        if (value == "Here") return emptyList()

        val match = xJunctionRegex.matchEntire(value)
            ?: throw IllegalArgumentException("XCM execution spec field $fieldName must be Here or X1..X8 junctions")
        val expectedCount = match.groupValues[1].toInt()
        val junctionValues = splitTopLevel(match.groupValues[2])

        require(junctionValues.size == expectedCount) {
            "XCM execution spec field $fieldName declares X$expectedCount but contains ${junctionValues.size} junctions"
        }

        return junctionValues.mapIndexed { index, junction ->
            parseJunction(junction, "$fieldName junction ${index + 1}")
        }
    }

    private fun parseJunction(value: String, fieldName: String): XcmJunctionSpec {
        val text = value.trim()
        val match = junctionRegex.matchEntire(text)
            ?: throw IllegalArgumentException("XCM execution spec field $fieldName has malformed junction")
        val typeText = match.groupValues[1]
        val argument = match.groupValues.getOrNull(2)
            ?.takeIf { it.isNotEmpty() }
            ?.trim()

        return when (typeText.normalizedJunctionType()) {
            "PARACHAIN" -> XcmJunctionSpec(
                XcmJunctionType.PARACHAIN,
                argument.requiredUnsignedInteger("$fieldName Parachain")
            )
            "PALLET_INSTANCE" -> XcmJunctionSpec(
                XcmJunctionType.PALLET_INSTANCE,
                argument.requiredUnsignedInteger("$fieldName PalletInstance")
            )
            "GENERAL_INDEX" -> XcmJunctionSpec(
                XcmJunctionType.GENERAL_INDEX,
                argument.requiredUnsignedInteger("$fieldName GeneralIndex")
            )
            "GENERAL_KEY" -> XcmJunctionSpec(
                XcmJunctionType.GENERAL_KEY,
                argument.requiredJunctionArgument("$fieldName GeneralKey")
            )
            "ACCOUNT_ID32" -> XcmJunctionSpec(
                XcmJunctionType.ACCOUNT_ID32,
                argument.requiredAccountPlaceholder("$fieldName AccountId32", accountField = "id")
            )
            "ACCOUNT_KEY20" -> XcmJunctionSpec(
                XcmJunctionType.ACCOUNT_KEY20,
                argument.requiredAccountPlaceholder("$fieldName AccountKey20", accountField = "key")
            )
            else -> throw IllegalArgumentException("XCM execution spec field $fieldName has unsupported junction $typeText")
        }
    }

    private fun splitTopLevel(value: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var parentheses = 0
        var braces = 0
        var brackets = 0

        value.forEachIndexed { index, char ->
            when (char) {
                '(' -> parentheses += 1
                ')' -> {
                    parentheses -= 1
                    require(parentheses >= 0) { "XCM execution spec multilocation has unbalanced parentheses" }
                }
                '{' -> braces += 1
                '}' -> {
                    braces -= 1
                    require(braces >= 0) { "XCM execution spec multilocation has unbalanced braces" }
                }
                '[' -> brackets += 1
                ']' -> {
                    brackets -= 1
                    require(brackets >= 0) { "XCM execution spec multilocation has unbalanced brackets" }
                }
                ',' -> if (parentheses == 0 && braces == 0 && brackets == 0) {
                    parts += value.substring(start, index).trim()
                    start = index + 1
                }
            }
        }

        require(parentheses == 0 && braces == 0 && brackets == 0) {
            "XCM execution spec multilocation has unbalanced delimiters"
        }

        parts += value.substring(start).trim()
        return parts.filter(String::isNotEmpty)
    }

    private fun String.normalizedJunctionType(): String {
        return trim()
            .replace('-', '_')
            .replace(' ', '_')
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .uppercase(Locale.US)
    }

    private fun String?.requiredUnsignedInteger(fieldName: String): String {
        val value = requiredJunctionArgument(fieldName)
        require(value.matches(Regex("^(0|[1-9][0-9]*)$"))) {
            "XCM execution spec field $fieldName must be a non-negative integer"
        }
        return value
    }

    private fun String?.requiredAccountPlaceholder(fieldName: String, accountField: String): String {
        val value = requiredJunctionArgument(fieldName)
        val expected = "{network: Any, $accountField: $ACCOUNT_PLACEHOLDER}"
        require(value == expected) {
            "XCM execution spec field $fieldName must use exact $expected recipient authority; " +
                "the <account> recipient placeholder must be the complete account field"
        }
        return value
    }

    private fun String?.requiredJunctionArgument(fieldName: String): String {
        val value = this?.trim().orEmpty()
        require(value.isNotEmpty()) { "XCM execution spec field $fieldName must include a value" }
        return value
    }
}

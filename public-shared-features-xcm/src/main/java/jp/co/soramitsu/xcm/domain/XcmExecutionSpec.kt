package jp.co.soramitsu.xcm.domain

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigInteger
import java.util.Locale

data class XcmExecutionSpec(
    val palletName: String,
    val callName: String,
    val transferType: XcmTransferType,
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
    LIMITED_TELEPORT_ASSETS
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
        destination: Chain.Xcm.Destination
    ): XcmExecutionSpec {
        val execution = destination.execution ?: error(
            "XCM execution spec missing for $assetSymbol from $originChainId to $destinationChainId"
        )

        val bridgeSpec = execution.bridge?.let {
            XcmBridgeExecutionSpec(
                parachainId = it.parachainId.requiredField("bridge.parachainId"),
                feeAssetLocation = it.feeAssetLocation.requiredMultiLocation("bridge.feeAssetLocation"),
                feeAssetItem = it.feeAssetItem.requiredNonNegative("bridge.feeAssetItem")
            )
        }

        val routeBridgeParachainId = destination.bridgeParachainId?.trim().orEmpty()
        require(routeBridgeParachainId.isEmpty() || bridgeSpec?.parachainId == routeBridgeParachainId) {
            "XCM bridge execution spec must match bridgeParachainId for $assetSymbol from $originChainId to $destinationChainId"
        }

        return XcmExecutionSpec(
            palletName = execution.palletName.requiredField("palletName"),
            callName = execution.callName.requiredField("callName"),
            transferType = execution.transferType.requiredTransferType(),
            xcmVersion = xcmVersion.requiredField("xcmVersion"),
            destinationLocation = execution.destinationLocation.requiredMultiLocation("destinationLocation"),
            assetLocation = execution.assetLocation.requiredMultiLocation("assetLocation"),
            beneficiaryLocation = execution.beneficiaryLocation.requiredMultiLocation("beneficiaryLocation"),
            feeAssetLocation = execution.feeAssetLocation.requiredMultiLocation("feeAssetLocation"),
            feeAssetItem = execution.feeAssetItem.requiredNonNegative("feeAssetItem"),
            weightLimit = execution.weightLimit.requiredWeightLimit(),
            destinationFee = execution.destinationFee.requiredDestinationFee(),
            bridge = bridgeSpec
        )
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
            else -> throw IllegalArgumentException("XCM execution spec transferType is unsupported")
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
                argument.requiredAccountPlaceholder("$fieldName AccountId32")
            )
            "ACCOUNT_KEY20" -> XcmJunctionSpec(
                XcmJunctionType.ACCOUNT_KEY20,
                argument.requiredAccountPlaceholder("$fieldName AccountKey20")
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

    private fun String?.requiredAccountPlaceholder(fieldName: String): String {
        val value = requiredJunctionArgument(fieldName)
        require(value.contains("<account>")) {
            "XCM execution spec field $fieldName must include <account> recipient placeholder"
        }
        return value
    }

    private fun String?.requiredJunctionArgument(fieldName: String): String {
        val value = this?.trim().orEmpty()
        require(value.isNotEmpty()) { "XCM execution spec field $fieldName must include a value" }
        return value
    }
}

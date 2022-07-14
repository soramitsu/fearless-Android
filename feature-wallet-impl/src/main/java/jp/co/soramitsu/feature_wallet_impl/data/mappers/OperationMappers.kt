package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.xnetworking.subquery.history.SubQueryHistoryItem
import jp.co.soramitsu.feature_account_api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionFilter
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigInteger
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val Operation.Type.operationAmount
    get() = when (this) {
        is Operation.Type.Extrinsic -> null
        is Operation.Type.Reward -> amount
        is Operation.Type.Transfer -> amount
    }

private val Operation.Type.operationStatus
    get() = when (this) {
        is Operation.Type.Extrinsic -> status
        is Operation.Type.Reward -> Operation.Status.COMPLETED
        is Operation.Type.Transfer -> status
    }

private val Operation.Type.operationFee
    get() = when (this) {
        is Operation.Type.Extrinsic -> fee
        is Operation.Type.Reward -> null
        is Operation.Type.Transfer -> fee
    }

val Operation.Type.hash
    get() = when (this) {
        is Operation.Type.Extrinsic -> hash
        is Operation.Type.Transfer -> hash
        is Operation.Type.Reward -> null
    }

private fun Operation.rewardOrNull() = type as? Operation.Type.Reward
private fun Operation.transferOrNull() = type as? Operation.Type.Transfer
private fun Operation.extrinsicOrNull() = type as? Operation.Type.Extrinsic

fun mapNodeToOperation(
    node: SubQueryHistoryItem,
    tokenType: Chain.Asset,
    myAddress: String,
): Operation {

    fun getParam(name: String): String? = node.data?.firstOrNull { it.paramName == name }?.paramValue

    val type: Operation.Type = when (node.module) {
        TransactionFilter.REWARD.name.lowercase() ->
            Operation.Type.Reward(
                amount = getParam("amount")?.toBigInteger() ?: BigInteger.ZERO,
                era = getParam("era")?.toInt() ?: 0,
                isReward = getParam("isReward")?.toBoolean() ?: false,
                validator = getParam("validator").orEmpty()
            )
        TransactionFilter.EXTRINSIC.name.lowercase() ->
            Operation.Type.Extrinsic(
                hash = getParam("hash").orEmpty(),
                module = getParam("module").orEmpty(),
                call = getParam("call").orEmpty(),
                fee = getParam("fee")?.toBigInteger() ?: BigInteger.ZERO,
                status = Operation.Status.fromSuccess(getParam("success")?.toBoolean() ?: false)
            )
        TransactionFilter.TRANSFER.name.lowercase() ->
            Operation.Type.Transfer(
                myAddress = myAddress,
                amount = getParam("amount")?.toBigInteger() ?: BigInteger.ZERO,
                receiver = getParam("to").orEmpty(),
                sender = getParam("from").orEmpty(),
                fee = getParam("fee")?.toBigInteger() ?: BigInteger.ZERO,
                status = Operation.Status.fromSuccess(getParam("success")?.toBoolean() ?: false),
                hash = getParam("extrinsicHash").orEmpty()
            )
        else -> throw IllegalStateException("All of the known operation type fields were null")
    }

    return Operation(
        id = node.id,
        address = myAddress,
        type = type,
        time = node.timestamp.toLong().toDuration(DurationUnit.SECONDS).inWholeMilliseconds,
        chainAsset = tokenType,
    )
}

private fun Chain.Asset.formatPlanks(planks: BigInteger, negative: Boolean): String {
    val amount = amountFromPlanks(planks)

    val withoutSign = amount.formatTokenAmount(this)
    val sign = if (negative) '-' else '+'

    return sign + withoutSign
}

private val Operation.Type.Transfer.isIncome
    get() = myAddress == receiver

private val Operation.Type.Transfer.displayAddress
    get() = if (isIncome) sender else receiver

private fun formatAmount(chainAsset: Chain.Asset, transfer: Operation.Type.Transfer): String {
    return chainAsset.formatPlanks(transfer.amount, negative = !transfer.isIncome)
}

private fun formatAmount(chainAsset: Chain.Asset, reward: Operation.Type.Reward): String {
    return chainAsset.formatPlanks(reward.amount, negative = !reward.isReward)
}

private fun formatFee(chainAsset: Chain.Asset, extrinsic: Operation.Type.Extrinsic): String {
    return chainAsset.formatPlanks(extrinsic.fee, negative = true)
}

private fun mapStatusToStatusAppearance(status: Operation.Status): OperationStatusAppearance {
    return when (status) {
        Operation.Status.COMPLETED -> OperationStatusAppearance.COMPLETED
        Operation.Status.FAILED -> OperationStatusAppearance.FAILED
        Operation.Status.PENDING -> OperationStatusAppearance.PENDING
    }
}

private fun Operation.Type.Extrinsic.formatted() = listOf(module, call).associateWith { formatted(it) }
private fun Operation.Type.Extrinsic.formattedAndReplaced() = listOf(module, call).associateWith {
    when (it) {
        call -> when {
            module == Modules.BALANCES && formatted(call) == "Transfer" -> "Transfer fee"
            module == Modules.CROWDLOAN && formatted(call) == "Contribute" -> "Contribute fee"
            else -> formatted(call)
        }
        else -> formatted(it)
    }
}

suspend fun mapOperationToOperationModel(
    operation: Operation,
    nameIdentifier: AddressDisplayUseCase.Identifier,
    resourceManager: ResourceManager,
    iconGenerator: AddressIconGenerator,
): OperationModel {
    val statusAppearance = mapStatusToStatusAppearance(operation.type.operationStatus)

    return with(operation) {
        when (val operationType = type) {
            is Operation.Type.Reward -> {
                OperationModel(
                    id = id,
                    time = time,
                    amount = formatAmount(chainAsset, operationType),
                    amountColorRes = if (operationType.isReward) R.color.green else R.color.white,
                    header = resourceManager.getString(
                        if (operationType.isReward) R.string.staking_reward else R.string.staking_slash
                    ),
                    statusAppearance = statusAppearance,
                    operationIcon = resourceManager.getDrawable(R.drawable.ic_staking),
                    subHeader = resourceManager.getString(R.string.tabbar_staking_title),
                )
            }

            is Operation.Type.Transfer -> {
                val amountColor = when {
                    operationType.status == Operation.Status.FAILED -> R.color.gray2
                    operationType.isIncome -> R.color.green
                    else -> R.color.white
                }

                OperationModel(
                    id = id,
                    time = time,
                    amount = formatAmount(chainAsset, operationType),
                    amountColorRes = amountColor,
                    header = nameIdentifier.nameOrAddress(operationType.displayAddress),
                    statusAppearance = statusAppearance,
                    operationIcon = iconGenerator.createAddressIcon(operationType.displayAddress, AddressIconGenerator.SIZE_BIG),
                    subHeader = resourceManager.getString(R.string.transfer_title),
                )
            }

            is Operation.Type.Extrinsic -> {

                val amountColor = if (operationType.status == Operation.Status.FAILED) R.color.gray2 else R.color.white

                OperationModel(
                    id = id,
                    time = time,
                    amount = formatFee(chainAsset, operationType),
                    amountColorRes = amountColor,
                    header = operationType.formattedAndReplaced()[operationType.call] ?: operationType.call,
                    statusAppearance = statusAppearance,
                    operationIcon = null,
                    subHeader = operationType.formattedAndReplaced()[operationType.module] ?: operationType.module,
                    assetIconUrl = chainAsset.iconUrl
                )
            }
        }
    }
}

fun mapOperationToParcel(
    operation: Operation,
    resourceManager: ResourceManager
): OperationParcelizeModel {
    with(operation) {
        return when (val operationType = operation.type) {
            is Operation.Type.Transfer -> {

                val feeOrZero = operationType.fee ?: BigInteger.ZERO

                val feeFormatted = operationType.fee?.let {
                    chainAsset.formatPlanks(it, negative = true)
                } ?: resourceManager.getString(R.string.common_unknown)

                val total = operationType.amount + feeOrZero

                OperationParcelizeModel.Transfer(
                    time = time,
                    address = address,
                    hash = operationType.hash,
                    amount = formatAmount(operation.chainAsset, operationType),
                    receiver = operationType.receiver,
                    sender = operationType.sender,
                    fee = feeFormatted,
                    isIncome = operationType.isIncome,
                    total = chainAsset.formatPlanks(total, negative = !operationType.isIncome),
                    statusAppearance = mapStatusToStatusAppearance(operationType.operationStatus)
                )
            }

            is Operation.Type.Reward -> {
                OperationParcelizeModel.Reward(
                    eventId = id,
                    address = address,
                    time = time,
                    amount = formatAmount(chainAsset, operationType),
                    isReward = operationType.isReward,
                    era = operationType.era,
                    validator = operationType.validator,
                )
            }

            is Operation.Type.Extrinsic -> {
                OperationParcelizeModel.Extrinsic(
                    time = time,
                    originAddress = address,
                    hash = operationType.hash,
                    module = operationType.formattedAndReplaced()[operationType.module] ?: operationType.module,
                    call = operationType.formattedAndReplaced()[operationType.call] ?: operationType.call,
                    fee = formatFee(chainAsset, operationType),
                    statusAppearance = mapStatusToStatusAppearance(operationType.operationStatus)
                )
            }
        }
    }
}

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.core_db.model.OperationLocal
import jp.co.soramitsu.feature_account_api.presenatation.account.AddressDisplayUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.SubqueryHistoryElementResponse
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigInteger
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

fun mapOperationStatusToOperationLocalStatus(status: Operation.Status) = when (status) {
    Operation.Status.PENDING -> OperationLocal.Status.PENDING
    Operation.Status.COMPLETED -> OperationLocal.Status.COMPLETED
    Operation.Status.FAILED -> OperationLocal.Status.FAILED
}

private fun mapOperationStatusLocalToOperationStatus(status: OperationLocal.Status) = when (status) {
    OperationLocal.Status.PENDING -> Operation.Status.PENDING
    OperationLocal.Status.COMPLETED -> Operation.Status.COMPLETED
    OperationLocal.Status.FAILED -> Operation.Status.FAILED
}

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

private val Operation.Type.hash
    get() = when (this) {
        is Operation.Type.Extrinsic -> hash
        is Operation.Type.Transfer -> hash
        is Operation.Type.Reward -> null
    }

private fun Operation.rewardOrNull() = type as? Operation.Type.Reward
private fun Operation.transferOrNull() = type as? Operation.Type.Transfer
private fun Operation.extrinsicOrNull() = type as? Operation.Type.Extrinsic

fun mapOperationToOperationLocalDb(
    operation: Operation,
    chainAsset: Chain.Asset,
    source: OperationLocal.Source,
): OperationLocal {
    val typeLocal = when (operation.type) {
        is Operation.Type.Transfer -> OperationLocal.Type.TRANSFER
        is Operation.Type.Reward -> OperationLocal.Type.REWARD
        is Operation.Type.Extrinsic -> OperationLocal.Type.EXTRINSIC
    }

    return with(operation) {
        OperationLocal(
            id = id,
            address = address,
            time = time,
            chainId = chainAsset.chainId,
            chainAssetId = chainAsset.id,
            module = extrinsicOrNull()?.module,
            call = extrinsicOrNull()?.call,
            amount = type.operationAmount,
            fee = type.operationFee,
            status = mapOperationStatusToOperationLocalStatus(type.operationStatus),
            source = source,
            operationType = typeLocal,
            sender = transferOrNull()?.sender,
            hash = type.hash,
            receiver = transferOrNull()?.receiver,
            isReward = rewardOrNull()?.isReward,
            era = rewardOrNull()?.era,
            validator = rewardOrNull()?.validator
        )
    }
}

fun mapOperationLocalToOperation(
    operationLocal: OperationLocal,
    chainAsset: Chain.Asset,
): Operation {
    with(operationLocal) {
        val operationType = when (operationType) {
            OperationLocal.Type.EXTRINSIC -> Operation.Type.Extrinsic(
                hash = hash!!,
                module = module!!,
                call = call!!,
                fee = fee!!,
                status = mapOperationStatusLocalToOperationStatus(status)
            )

            OperationLocal.Type.TRANSFER -> Operation.Type.Transfer(
                myAddress = address,
                amount = amount!!,
                receiver = receiver!!,
                sender = sender!!,
                fee = fee,
                status = mapOperationStatusLocalToOperationStatus(status),
                hash = hash
            )

            OperationLocal.Type.REWARD -> Operation.Type.Reward(
                amount = amount!!,
                isReward = isReward!!,
                era = era!!,
                validator = validator,
            )
        }

        return Operation(
            id = id,
            address = address,
            type = operationType,
            time = time,
            chainAsset = chainAsset,
        )
    }
}

@OptIn(ExperimentalTime::class)
fun mapNodeToOperation(
    node: SubqueryHistoryElementResponse.Query.HistoryElements.Node,
    tokenType: Chain.Asset,
): Operation {
    val type: Operation.Type = when {

        node.reward != null -> with(node.reward) {
            Operation.Type.Reward(
                amount = amount,
                era = era,
                isReward = isReward,
                validator = validator.nullIfEmpty()
            )
        }

        node.extrinsic != null -> with(node.extrinsic) {
            Operation.Type.Extrinsic(
                hash = hash,
                module = module,
                call = call,
                fee = fee,
                status = Operation.Status.fromSuccess(success)
            )
        }

        node.transfer != null -> with(node.transfer) {
            Operation.Type.Transfer(
                myAddress = node.address,
                amount = amount,
                receiver = to,
                sender = from,
                fee = fee,
                status = Operation.Status.fromSuccess(success),
                hash = extrinsicHash
            )
        }

        else -> throw IllegalStateException("All of the known operation type fields were null")
    }

    return Operation(
        id = node.id,
        address = node.address,
        type = type,
        time = node.timestamp.toLong().seconds.toLongMilliseconds(),
        chainAsset = tokenType,
    )
}

private val Chain.Asset.extrinsicIcon
    get() = when (this) {
        else -> R.drawable.ic_extrinsic_polkadot // TODO wallet - extrinsicIcon
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

private val CAMEL_CASE_REGEX = "(?<=[a-z])(?=[A-Z])".toRegex()

private fun String.camelCaseToCapitalizedWords() = CAMEL_CASE_REGEX.split(this).joinToString(separator = " ") { it.capitalize() }

private fun Operation.Type.Extrinsic.formattedCall() = call.camelCaseToCapitalizedWords()
private fun Operation.Type.Extrinsic.formattedModule() = module.camelCaseToCapitalizedWords()

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
                    header = operationType.formattedCall(),
                    statusAppearance = statusAppearance,
                    operationIcon = resourceManager.getDrawable(chainAsset.extrinsicIcon),
                    subHeader = operationType.formattedModule()
                )
            }
        }
    }
}

fun mapOperationToParcel(
    operation: Operation,
    resourceManager: ResourceManager,
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
                    module = operationType.formattedModule(),
                    call = operationType.formattedCall(),
                    fee = formatFee(chainAsset, operationType),
                    statusAppearance = mapStatusToStatusAppearance(operationType.operationStatus)
                )
            }
        }
    }
}

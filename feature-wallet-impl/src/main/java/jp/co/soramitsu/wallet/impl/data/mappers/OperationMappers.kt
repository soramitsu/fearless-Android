package jp.co.soramitsu.wallet.impl.data.mappers

import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.compose.theme.gray2
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.api.presentation.formatters.tokenAmountFromPlanks
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubqueryHistoryElementResponse
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.xnetworking.txhistory.TxHistoryItem
import java.math.BigInteger
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
        is Operation.Type.Swap -> baseAssetAmount
    }

private val Operation.Type.operationStatus
    get() = when (this) {
        is Operation.Type.Extrinsic -> status
        is Operation.Type.Reward -> Operation.Status.COMPLETED
        is Operation.Type.Transfer -> status
        is Operation.Type.Swap -> status
    }

private val Operation.Type.operationFee
    get() = when (this) {
        is Operation.Type.Extrinsic -> fee
        is Operation.Type.Reward -> null
        is Operation.Type.Transfer -> fee
        is Operation.Type.Swap -> networkFee
    }

private val Operation.Type.hash
    get() = when (this) {
        is Operation.Type.Extrinsic -> hash
        is Operation.Type.Transfer -> hash
        is Operation.Type.Reward -> null
        is Operation.Type.Swap -> hash
    }

private fun Operation.rewardOrNull() = type as? Operation.Type.Reward
private fun Operation.transferOrNull() = type as? Operation.Type.Transfer
private fun Operation.extrinsicOrNull() = type as? Operation.Type.Extrinsic
private fun Operation.swapOrNull() = type as? Operation.Type.Swap

fun mapOperationToOperationLocalDb(
    operation: Operation,
    source: OperationLocal.Source
): OperationLocal {
    val typeLocal = when (operation.type) {
        is Operation.Type.Transfer -> OperationLocal.Type.TRANSFER
        is Operation.Type.Reward -> OperationLocal.Type.REWARD
        is Operation.Type.Extrinsic -> OperationLocal.Type.EXTRINSIC
        is Operation.Type.Swap -> OperationLocal.Type.SWAP
    }

    return with(operation) {
        OperationLocal(
            id = id,
            address = address,
            time = time,
            chainId = operation.chainAsset.chainId,
            chainAssetId = operation.chainAsset.id,
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
            validator = rewardOrNull()?.validator,
            liquidityFee = swapOrNull()?.liquidityProviderFee,
            market = swapOrNull()?.selectedMarket,
            targetAssetId = swapOrNull()?.targetAsset?.id,
            targetAmount = swapOrNull()?.targetAssetAmount
        )
    }
}

fun mapOperationLocalToOperation(
    operationLocal: OperationLocal,
    chainAsset: Chain.Asset,
    chain: Chain
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
                validator = validator
            )

            OperationLocal.Type.SWAP -> Operation.Type.Swap(
                hash = hash.orEmpty(),
                module = module.orEmpty(),
                baseAssetAmount = amount.orZero(),
                liquidityProviderFee = liquidityFee.orZero(),
                selectedMarket = market,
                targetAssetAmount = targetAmount.orZero(),
                targetAsset = chain.assets.firstOrNull { it.id == targetAssetId },
                networkFee = fee.orZero(),
                status = mapOperationStatusLocalToOperationStatus(status)
            )
        }

        return Operation(
            id = id,
            address = address,
            type = operationType,
            time = time,
            chainAsset = chain.assets.firstOrNull { it.id == operationLocal.chainAssetId } ?: chainAsset
        )
    }
}

fun TxHistoryItem.toOperation(chain: Chain, chainAsset: Chain.Asset, accountAddress: String, filters: Set<TransactionFilter>): Operation? {
    val timeInMillis = timestamp.toLongOrNull()?.secondsToMillis() ?: 0
    val isTransferAllowed = filters.contains(TransactionFilter.TRANSFER) && method == "transfer"
    val isSwapAllowed = filters.contains(TransactionFilter.EXTRINSIC) && method == "swap"

    return when {
        isTransferAllowed -> Operation(
            id = id,
            address = accountAddress,
            time = timeInMillis,
            chainAsset = chainAsset,
            type = Operation.Type.Transfer(
                hash = blockHash,
                myAddress = data?.firstOrNull { it.paramName == "from" }?.paramValue.orEmpty(),
                amount = chainAsset.planksFromAmount(data?.firstOrNull { it.paramName == "amount" }?.paramValue?.toBigDecimal().orZero()),
                receiver = data?.firstOrNull { it.paramName == "to" }?.paramValue.orEmpty(),
                sender = data?.firstOrNull { it.paramName == "from" }?.paramValue.orEmpty(),
                status = Operation.Status.fromSuccess(success),
                fee = chainAsset.planksFromAmount(networkFee.toBigDecimal().orZero())
            )
        )
        isSwapAllowed -> {
            val baseCurrencyId = data?.firstOrNull { it.paramName == "baseAssetId" }?.paramValue
            val baseAsset = chain.assets.firstOrNull { it.currencyId == baseCurrencyId } ?: return null
            val baseAssetAmount = data?.firstOrNull { it.paramName == "baseAssetAmount" }?.paramValue?.toBigDecimal().orZero()

            val targetCurrencyId = data?.firstOrNull { it.paramName == "targetAssetId" }?.paramValue
            val targetAsset = chain.assets.firstOrNull { it.currencyId == targetCurrencyId }
            val targetAssetAmount = data?.firstOrNull { it.paramName == "targetAssetAmount" }?.paramValue?.toBigDecimal().orZero()

            val liquidityProviderFee = data?.firstOrNull { it.paramName == "liquidityProviderFee" }?.paramValue?.toBigDecimal().orZero()

            Operation(
                id = id,
                address = accountAddress,
                time = timeInMillis,
                chainAsset = baseAsset,
                type = Operation.Type.Swap(
                    hash = blockHash,
                    module = module,
                    baseAssetAmount = baseAsset.planksFromAmount(baseAssetAmount),
                    liquidityProviderFee = chainAsset.planksFromAmount(liquidityProviderFee),
                    selectedMarket = data?.firstOrNull { it.paramName == "selectedMarket" }?.paramValue,
                    targetAsset = targetAsset,
                    targetAssetAmount = targetAsset?.planksFromAmount(targetAssetAmount),
                    networkFee = chainAsset.planksFromAmount(networkFee.toBigDecimal().orZero()),
                    status = Operation.Status.fromSuccess(success)
                )
            )
        }
        else -> null
    }
}

private fun Long.secondsToMillis() = toDuration(DurationUnit.SECONDS).inWholeMilliseconds

fun mapNodeToOperation(
    node: SubqueryHistoryElementResponse.Query.HistoryElements.Node,
    tokenType: Chain.Asset
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
        time = node.timestamp.toLong().toDuration(DurationUnit.SECONDS).inWholeMilliseconds,
        chainAsset = tokenType
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

private fun formatSwapInfo(chainAsset: Chain.Asset, swap: Operation.Type.Swap): String {
    return swap.baseAssetAmount.tokenAmountFromPlanks(chainAsset) +
        swap.targetAsset?.let { " ➝ " + swap.targetAssetAmount?.tokenAmountFromPlanks(it) }.orEmpty()
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
    iconGenerator: AddressIconGenerator
): OperationModel {
    val statusAppearance = mapStatusToStatusAppearance(operation.type.operationStatus)

    return with(operation) {
        when (val operationType = type) {
            is Operation.Type.Reward -> {
                OperationModel(
                    id = id,
                    time = time,
                    amount = formatAmount(chainAsset, operationType),
                    amountColor = if (operationType.isReward) greenText else white,
                    header = resourceManager.getString(
                        if (operationType.isReward) R.string.staking_reward else R.string.staking_slash
                    ),
                    statusAppearance = statusAppearance,
                    operationIcon = resourceManager.getDrawable(R.drawable.ic_staking),
                    subHeader = resourceManager.getString(R.string.tabbar_staking_title)
                )
            }

            is Operation.Type.Transfer -> {
                val amountColor = when {
                    operationType.status == Operation.Status.FAILED -> gray2
                    operationType.isIncome -> greenText
                    else -> white
                }

                OperationModel(
                    id = id,
                    time = time,
                    amount = formatAmount(chainAsset, operationType),
                    amountColor = amountColor,
                    header = nameIdentifier.nameOrAddress(operationType.displayAddress),
                    statusAppearance = statusAppearance,
                    operationIcon = iconGenerator.createAddressIcon(operationType.displayAddress, AddressIconGenerator.SIZE_BIG),
                    subHeader = resourceManager.getString(R.string.transfer_title)
                )
            }

            is Operation.Type.Extrinsic -> {
                OperationModel(
                    id = id,
                    time = time,
                    amount = formatFee(chainAsset, operationType),
                    amountColor = if (operationType.status == Operation.Status.FAILED) gray2 else white,
                    header = operationType.formattedAndReplaced()[operationType.call] ?: operationType.call,
                    statusAppearance = statusAppearance,
                    operationIcon = null,
                    subHeader = operationType.formattedAndReplaced()[operationType.module] ?: operationType.module,
                    assetIconUrl = chainAsset.iconUrl
                )
            }

            is Operation.Type.Swap -> {
                OperationModel(
                    id = id,
                    time = time,
                    amount = formatSwapInfo(chainAsset, operationType),
                    amountColor = if (operationType.status == Operation.Status.FAILED) gray2 else greenText,
                    header = resourceManager.getString(R.string.polkaswap_confirmation_swap_stub),
                    statusAppearance = statusAppearance,
                    operationIcon = resourceManager.getDrawable(R.drawable.ic_swap_history),
                    subHeader = when (operationType.status) {
                        Operation.Status.COMPLETED -> resourceManager.getString(R.string.polkaswap_confirmation_swapped_stub)
                        else -> resourceManager.getString(statusAppearance.labelRes)
                    }
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
                    validator = operationType.validator
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

            is Operation.Type.Swap -> {
                OperationParcelizeModel.Swap(
                    id = id,
                    address = address,
                    chainAsset = chainAsset,
                    targetAsset = operationType.targetAsset,
                    time = time,
                    hash = operationType.hash,
                    module = operationType.module,
                    baseAssetAmount = operationType.baseAssetAmount,
                    liquidityProviderFee = operationType.liquidityProviderFee,
                    selectedMarket = operationType.selectedMarket,
                    targetAssetAmount = operationType.targetAssetAmount,
                    networkFee = operationType.networkFee,
                    status = operationType.status
                )
            }
        }
    }
}

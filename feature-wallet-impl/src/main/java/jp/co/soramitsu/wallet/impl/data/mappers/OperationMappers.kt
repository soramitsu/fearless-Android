package jp.co.soramitsu.wallet.impl.data.mappers

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.address.createEthereumAddressIcon
import jp.co.soramitsu.common.compose.component.AddressDisplayState
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.gray2
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.toWalletAddress
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.toMarkets
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoDetailFromPlanks
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoFromPlanks
import jp.co.soramitsu.wallet.api.presentation.formatters.formatSigned
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubqueryHistoryElementResponse
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.SwapDetailState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransactionDetailsState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.TransferDetailsState
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap.mapToStatusAppearance
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxHistoryItem
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import org.ton.block.AddrStd

// VAL
const val SORA_REWARD_ASSET_ID =
    "0x0200040000000000000000000000000000000000000000000000000000000000"

// XOR
const val SORA_STAKING_CURRENCY_ID =
    "0x0200000000000000000000000000000000000000000000000000000000000000"

fun mapOperationStatusToOperationLocalStatus(status: Operation.Status) = when (status) {
    Operation.Status.PENDING -> OperationLocal.Status.PENDING
    Operation.Status.COMPLETED -> OperationLocal.Status.COMPLETED
    Operation.Status.FAILED -> OperationLocal.Status.FAILED
}

private fun mapOperationStatusLocalToOperationStatus(status: OperationLocal.Status) =
    when (status) {
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
    chainAsset: Asset,
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
            chainAsset = chain.assets.firstOrNull { it.id == operationLocal.chainAssetId }
                ?: chainAsset
        )
    }
}

fun TxHistoryItem.toOperation(
    chain: Chain,
    chainAsset: Asset,
    accountAddress: String,
    filters: Set<TransactionFilter>
): Operation? {
    val timeInMillis = timestamp.toLongOrNull()?.secondsToMillis() ?: 0
    val isTransferAllowed =
        filters.contains(TransactionFilter.TRANSFER) && method.lowercase() == "transfer"
    val isSwapAllowed =
        filters.contains(TransactionFilter.EXTRINSIC) && method.lowercase() == "swap"
    val isRewardAllowed =
        filters.contains(TransactionFilter.REWARD) && method.lowercase() == "rewarded" && chainAsset.currencyId == SORA_REWARD_ASSET_ID
    val stakingAllowed =
        filters.contains(TransactionFilter.EXTRINSIC) && module.lowercase() == "staking" && method.lowercase() != "rewarded" && chainAsset.currencyId == SORA_STAKING_CURRENCY_ID

    return when {
        isTransferAllowed -> {
            val currencyId = data?.firstOrNull { it.paramName == "assetId" }?.paramValue
            if (currencyId != chainAsset.currencyId) return null

            Operation(
                id = id,
                address = accountAddress,
                time = timeInMillis,
                chainAsset = chainAsset,
                type = Operation.Type.Transfer(
                    hash = id,
                    myAddress = data?.firstOrNull { it.paramName == "from" }?.paramValue.orEmpty(),
                    amount = chainAsset.planksFromAmount(
                        data?.firstOrNull { it.paramName == "amount" }?.paramValue?.toBigDecimalOrNull()
                            .orZero()
                    ),
                    receiver = data?.firstOrNull { it.paramName == "to" }?.paramValue.orEmpty(),
                    sender = data?.firstOrNull { it.paramName == "from" }?.paramValue.orEmpty(),
                    status = Operation.Status.fromSuccess(success),
                    fee = runCatching { networkFee.toBigInteger() }.getOrNull()
                )
            )
        }

        isSwapAllowed -> {
            val baseCurrencyId = data?.firstOrNull { it.paramName == "baseAssetId" }?.paramValue
            val targetCurrencyId = data?.firstOrNull { it.paramName == "targetAssetId" }?.paramValue
            if (chainAsset.currencyId !in listOf(baseCurrencyId, targetCurrencyId)) return null

            val baseAsset =
                chain.assets.firstOrNull { it.currencyId == baseCurrencyId } ?: return null
            val baseAssetAmount =
                data?.firstOrNull { it.paramName == "baseAssetAmount" }?.paramValue?.toBigDecimalOrNull()
                    .orZero()

            val targetAsset = chain.assets.firstOrNull { it.currencyId == targetCurrencyId }
            val targetAssetAmount =
                data?.firstOrNull { it.paramName == "targetAssetAmount" }?.paramValue?.toBigDecimalOrNull()
                    .orZero()

            val liquidityProviderFee =
                data?.firstOrNull { it.paramName == "liquidityProviderFee" }?.paramValue?.toBigDecimalOrNull()
                    .orZero()

            Operation(
                id = id,
                address = accountAddress,
                time = timeInMillis,
                chainAsset = baseAsset,
                type = Operation.Type.Swap(
                    hash = id,
                    module = module,
                    baseAssetAmount = baseAsset.planksFromAmount(baseAssetAmount),
                    liquidityProviderFee = chainAsset.planksFromAmount(liquidityProviderFee),
                    selectedMarket = data?.firstOrNull { it.paramName == "selectedMarket" }?.paramValue,
                    targetAsset = targetAsset,
                    targetAssetAmount = targetAsset?.planksFromAmount(targetAssetAmount),
                    networkFee = runCatching { networkFee.toBigInteger().orZero() }.getOrNull()
                        .orZero(),
                    status = Operation.Status.fromSuccess(success)
                )
            )
        }

        isRewardAllowed -> {
            Operation(
                id = id,
                address = accountAddress,
                time = timeInMillis,
                chainAsset = chainAsset,
                type = Operation.Type.Reward(
                    amount = chainAsset.planksFromAmount(
                        data?.firstOrNull { it.paramName == "amount" }?.paramValue?.toBigDecimalOrNull()
                            .orZero()
                    ),
                    isReward = true,
                    era = data?.firstOrNull { it.paramName == "era" }?.paramValue?.toIntOrNull()
                        ?: 0,
                    validator = null
                )
            )
        }

        stakingAllowed -> {
            Operation(
                id = id,
                address = accountAddress,
                time = timeInMillis,
                chainAsset = chainAsset,
                type = Operation.Type.Extrinsic(
                    hash = id,
                    module = module,
                    call = method,
                    status = Operation.Status.fromSuccess(success),
                    fee = runCatching { networkFee.toBigInteger().orZero() }.getOrNull().orZero()
                )
            )
        }

        else -> null

    }
}

private fun Long.secondsToMillis() = toDuration(DurationUnit.SECONDS).inWholeMilliseconds

fun mapNodeToOperation(
    node: SubqueryHistoryElementResponse.Query.HistoryElements.Node,
    tokenType: Asset
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

private val Operation.Type.Transfer.isIncome
    get() = myAddress == receiver

private val Operation.Type.Transfer.displayAddress: String
    get() {
        val address =  if (isIncome) sender else receiver
        return "${address.take(8)}...${address.takeLast(8)}"
    }

private val Operation.Type.Transfer.partnerAddress: String
    get() = if (isIncome) sender else receiver

private fun formatDetailsAmount(chainAsset: Asset, transfer: Operation.Type.Transfer): String {
    return transfer.amount.formatCryptoDetailFromPlanks(chainAsset).formatSigned(transfer.isIncome)
}

private fun formatDetailsAmount(chainAsset: Asset, reward: Operation.Type.Reward): String {
    return reward.amount.formatCryptoDetailFromPlanks(chainAsset).formatSigned(reward.isReward)
}

private fun formatSwapInfo(chainAsset: Asset, swap: Operation.Type.Swap): String {
    val baseAmountFormatted = swap.baseAssetAmount.formatCryptoFromPlanks(chainAsset)
    val targetAmountFormatted =
        swap.targetAsset?.let { swap.targetAssetAmount.orZero().formatCryptoFromPlanks(it) }
    return baseAmountFormatted + swap.targetAsset?.let { " ➝ $targetAmountFormatted" }.orEmpty()
}

private fun BigInteger.formatFee(chainAsset: Asset): String {
    return formatCryptoDetailFromPlanks(chainAsset).formatSigned(false)
}

private fun mapStatusToStatusAppearance(status: Operation.Status): OperationStatusAppearance {
    return when (status) {
        Operation.Status.COMPLETED -> OperationStatusAppearance.COMPLETED
        Operation.Status.FAILED -> OperationStatusAppearance.FAILED
        Operation.Status.PENDING -> OperationStatusAppearance.PENDING
    }
}

private fun Operation.Type.Extrinsic.formatted() =
    listOf(module, call).associateWith { formatted(it) }

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
    resourceManager: ResourceManager,
    iconGenerator: AddressIconGenerator,
    ecosystem: Ecosystem
): OperationModel {
    val statusAppearance = mapStatusToStatusAppearance(operation.type.operationStatus)

    return with(operation) {
        when (val operationType = type) {
            is Operation.Type.Reward -> {
                OperationModel(
                    id = id,
                    time = time,
                    amount = formatDetailsAmount(chainAsset, operationType),
                    amountColor = if (operationType.isReward) greenText else white,
                    header = resourceManager.getString(
                        if (operationType.isReward) R.string.staking_reward else R.string.staking_slash
                    ),
                    statusAppearance = statusAppearance,
                    operationIcon = resourceManager.getDrawable(R.drawable.ic_staking),
                    subHeader = resourceManager.getString(R.string.tabbar_staking_title),
                    type = operationType.toModel()
                )
            }

            is Operation.Type.Transfer -> {
                createTransferOperationModel(
                    operationType,
                    ecosystem,
                    iconGenerator,
                    statusAppearance,
                    resourceManager
                )
            }

            is Operation.Type.Extrinsic -> {
                OperationModel(
                    id = id,
                    time = time,
                    amount = operationType.fee.formatFee(chainAsset),
                    amountColor = if (operationType.status == Operation.Status.FAILED) gray2 else white,
                    header = operationType.formattedAndReplaced()[operationType.call]
                        ?: operationType.call,
                    statusAppearance = statusAppearance,
                    operationIcon = null,
                    subHeader = operationType.formattedAndReplaced()[operationType.module]
                        ?: operationType.module,
                    type = operationType.toModel(),
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
                    },
                    type = operationType.toModel()
                )
            }
        }
    }
}

private suspend fun Operation.createTransferOperationModel(
    operationType: Operation.Type.Transfer,
    ecosystem: Ecosystem,
    iconGenerator: AddressIconGenerator,
    statusAppearance: OperationStatusAppearance,
    resourceManager: ResourceManager
): OperationModel {
    val amountColor = when {
        operationType.status == Operation.Status.FAILED -> gray2
        operationType.isIncome -> greenText
        else -> white
    }

    val operationIcon = when (ecosystem) {
        Ecosystem.EthereumBased, Ecosystem.Ethereum -> {
            iconGenerator.createEthereumAddressIcon(
                operationType.partnerAddress,
                AddressIconGenerator.SIZE_MEDIUM
            )
        }

        Ecosystem.Substrate -> {
            iconGenerator.createAddressIcon(
                operationType.partnerAddress,
                AddressIconGenerator.SIZE_BIG
            )
        }

        Ecosystem.Ton -> {
            iconGenerator.createWalletIcon(
                WalletEcosystem.Ton,
                AddressIconGenerator.SIZE_BIG
            )
        }

        else -> null
    }

    return OperationModel(
        id = id,
        time = time,
        amount = formatDetailsAmount(chainAsset, operationType),
        amountColor = amountColor,
        header = operationType.displayAddress,
        statusAppearance = statusAppearance,
        operationIcon = operationIcon,
        subHeader = resourceManager.getString(R.string.transfer_title),
        type = operationType.toModel()
    )
}

suspend fun mapOperationToTransactionDetailsState(
    operation: Operation,
    resourceManager: ResourceManager,
    iconGenerator: AddressIconGenerator,
    chain: Chain
): TransactionDetailsState {
    val statusAppearance = mapStatusToStatusAppearance(operation.type.operationStatus)

    return with(operation) {
        when (val operationType = type) {
            is Operation.Type.Reward -> {

                createRewardDetailsState(
                    resourceManager,
                    operationType,
                    iconGenerator,
                    statusAppearance
                )
            }

            is Operation.Type.Transfer -> {
                createTransferDetailsState(
                    operationType,
                    chain,
                    iconGenerator,
                    resourceManager,
                    statusAppearance
                )
            }

            is Operation.Type.Extrinsic -> {
                createExtrinsicDetailsState(
                    chain,
                    iconGenerator,
                    resourceManager,
                    statusAppearance,
                    operationType
                )
            }

            is Operation.Type.Swap -> {
                createSwapDetailState(operationType, chain)
            }
        }
    }
}

private fun Operation.createSwapDetailState(
    operationType: Operation.Type.Swap,
    chain: Chain
): SwapDetailState {
    val swapRate =
        operationType.targetAsset?.amountFromPlanks(operationType.targetAssetAmount.orZero())
            .orZero().divide(
                chainAsset.amountFromPlanks(operationType.baseAssetAmount),
                RoundingMode.HALF_DOWN
            )

    val hasSubscanUrl = chain.explorers.any { it.type == Chain.Explorer.Type.SUBSCAN }
    return SwapDetailState(
        fromTokenImage = GradientIconState.Remote(chainAsset.iconUrl, chainAsset.color),
        toTokenImage = GradientIconState.Remote(
            operationType.targetAsset?.iconUrl.orEmpty(),
            operationType.targetAsset?.color.orEmpty()
        ),
        fromTokenAmount = operationType.baseAssetAmount.formatCryptoFromPlanks(
            chainAsset
        ),
        toTokenAmount = operationType.targetAsset?.let {
            operationType.targetAssetAmount?.formatCryptoFromPlanks(
                it
            )
        } ?: "???",
        fromTokenName = chainAsset.symbol.uppercase(),
        toTokenName = operationType.targetAsset?.symbol?.uppercase() ?: "???",
        statusAppearance = operationType.status.mapToStatusAppearance(),
        address = address,
        addressName = null,
        hash = operationType.hash,
        fromTokenOnToToken = swapRate.formatCryptoDetail(),
        liquidityProviderFee = operationType.liquidityProviderFee.formatCryptoDetailFromPlanks(
            chainAsset
        ),
        networkFee = operationType.networkFee.formatCryptoDetailFromPlanks(chainAsset),
        time = time,
        market = operationType.selectedMarket?.let {
            listOf(it).toMarkets().firstOrNull()
        } ?: Market.SMART,
        isShowSubscanButtons = hasSubscanUrl
    )
}

private suspend fun Operation.createExtrinsicDetailsState(
    chain: Chain,
    iconGenerator: AddressIconGenerator,
    resourceManager: ResourceManager,
    statusAppearance: OperationStatusAppearance,
    operationType: Operation.Type.Extrinsic
): TransferDetailsState {
    val senderIcon: Any = when (chain.ecosystem) {
        Ecosystem.EthereumBased, Ecosystem.Ethereum -> {
            iconGenerator.createEthereumAddressIcon(
                address,
                AddressIconGenerator.SIZE_MEDIUM
            )
        }

        Ecosystem.Substrate -> {
            iconGenerator.createAddressIcon(address, AddressIconGenerator.SIZE_BIG)
        }

        Ecosystem.Ton -> {
            chainAsset.iconUrl
        }
    }

    val sender = when (chain.ecosystem) {
        Ecosystem.Ton -> {
            AddrStd(address).toWalletAddress(chain.isTestNet)
        }

        else -> address
    }

    val tableItems = mutableListOf(
        TitleValueViewState(
            title = resourceManager.getString(R.string.common_date),
            value = time.formatDateTime()
        )
    )

    if (operationType.module.isNotEmpty()) {
        tableItems.add(
            TitleValueViewState(
                title = resourceManager.getString(R.string.common_module),
                value = operationType.module
            )
        )
    }

    if (operationType.call.isNotEmpty()) {
        tableItems.add(
            TitleValueViewState(
                title = resourceManager.getString(R.string.common_call),
                value = operationType.call
            )
        )
    }

    tableItems.add(
        TitleValueViewState(
            title = resourceManager.getString(R.string.choose_amount_fee),
            value = operationType.fee.formatFee(chainAsset)
        )
    )

    return TransferDetailsState(
        hash = TextInputViewState(
            text = type.hash ?: id,
            hint = resourceManager.getString(R.string.common_details),
            isActive = false,
            endIcon = R.drawable.ic_more_vertical
        ),
        firstAddress = AddressDisplayState(
            title = resourceManager.getString(R.string.transaction_details_from),
            input = sender,
            image = senderIcon,
            endIcon = R.drawable.ic_more_vertical
        ),
        secondAddress = null,
        status = statusAppearance,
        items = tableItems
    )
}

private suspend fun Operation.createTransferDetailsState(
    operationType: Operation.Type.Transfer,
    chain: Chain,
    iconGenerator: AddressIconGenerator,
    resourceManager: ResourceManager,
    statusAppearance: OperationStatusAppearance
): TransferDetailsState {
    val amountColor = when {
        operationType.status == Operation.Status.FAILED -> gray2
        operationType.isIncome -> greenText
        else -> white
    }
    val senderIcon: Any = when (chain.ecosystem) {
        Ecosystem.EthereumBased, Ecosystem.Ethereum -> {
            iconGenerator.createEthereumAddressIcon(
                operationType.sender,
                AddressIconGenerator.SIZE_MEDIUM
            )
        }

        Ecosystem.Substrate -> {
            iconGenerator.createAddressIcon(
                operationType.sender,
                AddressIconGenerator.SIZE_BIG
            )
        }

        Ecosystem.Ton -> {
            chainAsset.iconUrl
        }
    }
    val receiverIcon: Any = when (chain.ecosystem) {
        Ecosystem.EthereumBased, Ecosystem.Ethereum -> {
            iconGenerator.createEthereumAddressIcon(
                operationType.receiver,
                AddressIconGenerator.SIZE_MEDIUM
            )
        }

        Ecosystem.Substrate -> {
            iconGenerator.createAddressIcon(
                operationType.receiver,
                AddressIconGenerator.SIZE_BIG
            )
        }

        Ecosystem.Ton -> {
            chainAsset.iconUrl
        }
    }

    return TransferDetailsState(
        hash = TextInputViewState(
            text = type.hash ?: id,
            hint = resourceManager.getString(R.string.hash),
            isActive = false,
            endIcon = R.drawable.ic_more_vertical
        ),
        firstAddress = AddressDisplayState(
            title = resourceManager.getString(R.string.transaction_details_from),
            input = operationType.sender,
            image = senderIcon,
            endIcon = R.drawable.ic_more_vertical
        ),
        secondAddress = AddressDisplayState(
            title = resourceManager.getString(R.string.choose_amount_to),
            input = operationType.receiver,
            image = receiverIcon,
            endIcon = R.drawable.ic_more_vertical
        ),
        status = statusAppearance,
        listOf(
            TitleValueViewState(
                title = resourceManager.getString(R.string.common_date),
                value = time.formatDateTime()
            ),
            TitleValueViewState(
                title = resourceManager.getString(R.string.common_amount),
                value = formatDetailsAmount(chainAsset, operationType),
                valueColor = amountColor
            ),
            TitleValueViewState(
                title = resourceManager.getString(R.string.choose_amount_fee),
                value = operationType.fee?.formatFee(chainAsset)
            )
        )
    )
}

private suspend fun Operation.createRewardDetailsState(
    resourceManager: ResourceManager,
    operationType: Operation.Type.Reward,
    iconGenerator: AddressIconGenerator,
    statusAppearance: OperationStatusAppearance
): TransferDetailsState {
    val timeStr = time.formatDateTime()

    return TransferDetailsState(
        hash = TextInputViewState(
            text = id,
            hint = resourceManager.getString(R.string.common_event),
            isActive = false,
            endIcon = R.drawable.ic_more_vertical
        ),
        firstAddress = operationType.validator?.let {
            val validatorAddressModel = iconGenerator.createAddressModel(it, 32, null)
            AddressDisplayState(
                title = resourceManager.getString(R.string.staking_reward_details_validator),
                input = it,
                image = validatorAddressModel.image,
                endIcon = R.drawable.ic_more_vertical
            )
        },
        secondAddress = null,
        status = statusAppearance,
        listOf(
            TitleValueViewState(
                title = resourceManager.getString(R.string.common_date),
                value = timeStr
            ),
            TitleValueViewState(
                title = resourceManager.getString(R.string.staking_reward_details_era),
                value = operationType.era.toString()
            ),
            TitleValueViewState(
                title = resourceManager.getString(R.string.staking_reward),
                value = formatDetailsAmount(chainAsset, operationType)
            ),
        )
    )
}

private fun Operation.Type.toModel(): OperationModel.Type {
    return when (this) {
        is Operation.Type.Extrinsic -> OperationModel.Type.Extrinsic
        is Operation.Type.Reward -> OperationModel.Type.Reward
        is Operation.Type.Swap -> OperationModel.Type.Swap
        is Operation.Type.Transfer -> OperationModel.Type.Transfer
    }
}

fun String.toBigDecimalOrNull(): BigDecimal? {
    return runCatching { toBigDecimal() }.getOrNull()
}
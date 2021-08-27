package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.common.utils.secondsToMilliseconds
import jp.co.soramitsu.core_db.model.OperationLocal
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeLocalToTokenType
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeToTokenTypeLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.SubqueryHistoryElementResponse
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationModel
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

fun mapOperationStatusToOperationLocalStatus(status: Operation.Status) = when (status) {
    Operation.Status.PENDING -> OperationLocal.Status.PENDING
    Operation.Status.COMPLETED -> OperationLocal.Status.COMPLETED
    Operation.Status.FAILED -> OperationLocal.Status.FAILED
}

fun mapOperationToOperationLocalDb(operation: Operation, source: OperationLocal.Source): OperationLocal {
    with(operation) {
        val operationLocal = OperationLocal(
            hash = hash,
            address = address,
            time = time,
            tokenType = mapTokenTypeToTokenTypeLocal(tokenType),
            type = transactionType.header,
            call = transactionType.subheader,
            amount = transactionType.operationAmount.toBigInteger(),
            fee = transactionType.operationFee.toBigInteger(),
            status = mapOperationStatusToOperationLocalStatus(operation.transactionType.status),
            source = source
        )

        return when (transactionType) {
            is Operation.TransactionType.Transfer -> {
                operationLocal.copy(
                    sender = (transactionType as Operation.TransactionType.Transfer).sender,
                    receiver = (transactionType as Operation.TransactionType.Transfer).receiver
                )
            }
            is Operation.TransactionType.Extrinsic -> {
                operationLocal.copy(
                    success = (transactionType as? Operation.TransactionType.Extrinsic)?.success
                )
            }
            is Operation.TransactionType.Reward -> {
                operationLocal.copy(
                    isReward = (transactionType as Operation.TransactionType.Reward).isReward,
                    era = (transactionType as Operation.TransactionType.Reward).era,
                    validator = (transactionType as Operation.TransactionType.Reward).validator,
                )
            }
        }
    }
}

fun mapOperationLocalToOperation(operationLocal: OperationLocal, accountName: String?): Operation {
    with(operationLocal) {
        val operation = if (type != null && call != null && call != "Staking") {
            Operation.TransactionType.Extrinsic(
                hash = hash,
                module = type!!,
                call = call!!,
                fee = (fee?.toBigDecimal())!!,
                success = success!!
            )
        } else if (call == "Transfer") {
            Operation.TransactionType.Transfer(
                amount = (amount?.toBigDecimal())!!,
                receiver = receiver!!,
                sender = sender!!,
                fee = (fee?.toBigDecimal())!!
            )
        } else {
            Operation.TransactionType.Reward(
                amount = (amount?.toBigDecimal())!!,
                isReward = isReward!!,
                era = era!!,
                validator = validator!!
            )
        }

        return Operation(
            hash = hash,
            address = address,
            accountName = accountName,
            transactionType = operation,
            time = time,
            tokenType = mapTokenTypeLocalToTokenType(tokenType),
        )
    }
}

@ExperimentalTime
fun mapNodeToOperation(
    node: SubqueryHistoryElementResponse.Query.HistoryElements.Node,
    cursor: String,
    currentAccount: WalletAccount,
    accountName: String?
): Operation {
    val token = Token.Type.fromNetworkType(currentAccount.network.type)
    val type: Operation.TransactionType?
    when {
        node.reward != null -> {
            type = Operation.TransactionType.Reward(
                amount = token.amountFromPlanks(node.reward.amount.toBigInteger()),
                era = node.reward.era,
                isReward = node.reward.isReward,
                validator = node.reward.validator
            )
        }
        node.extrinsic != null -> {
            type = Operation.TransactionType.Extrinsic(
                hash = node.extrinsic.hash,
                module = node.extrinsic.module,
                call = node.extrinsic.call,
                fee = token.amountFromPlanks(node.extrinsic.fee.toBigInteger()),
                success = node.extrinsic.success
            )
        }
        node.transfer != null -> {
            type = Operation.TransactionType.Transfer(
                amount = token.amountFromPlanks(node.transfer.amount.toBigInteger()),
                receiver = node.transfer.to,
                sender = node.transfer.from,
                fee = token.amountFromPlanks(node.transfer.fee.toBigInteger())
            )
        }
        else -> {
            throw Exception()
        }
    }

    return Operation(
        hash = node.id,
        address = node.address,
        transactionType = type,
        time = node.timestamp.toLong().secondsToMilliseconds(),
        tokenType = token,
        accountName = accountName,
        nextPageCursor = cursor
    )
}

fun mapOperationToOperationModel(operation: Operation): OperationModel {
    with(operation) {
        return OperationModel(
            hash = hash,
            address = address,
            accountName = accountName,
            transactionType = transactionType,
            time = time,
            tokenType = tokenType
        )
    }
}

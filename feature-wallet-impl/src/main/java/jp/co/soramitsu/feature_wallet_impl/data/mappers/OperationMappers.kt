package jp.co.soramitsu.feature_wallet_impl.data.mappers

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

@ExperimentalTime
fun mapOperationToOperationLocalDb(operation: Operation, source: OperationLocal.Source): OperationLocal {
    with(operation) {
        val operationLocal = OperationLocal(
            hash = hash,
            address = address,
            time = time.seconds.toLongMilliseconds(),
            tokenType = mapTokenTypeToTokenTypeLocal(tokenType),
            type = transactionType.getHeader(),
            call = transactionType.getSubheader(),
            amount = transactionType.operationAmount.toBigInteger(),
            fee = transactionType.operationFee.toBigInteger(),
            status = mapOperationStatusToOperationLocalStatus(operation.transactionType.status),
            source = source,
            operationType = transactionType.getOperationType()
        )

        return when (val type = transactionType) {
            is Operation.TransactionType.Transfer -> {
                operationLocal.copy(
                    sender = type.sender,
                    receiver = type.receiver,
                )
            }
            is Operation.TransactionType.Extrinsic -> {
                operationLocal.copy(
                    success = type.success
                )
            }
            is Operation.TransactionType.Reward -> {
                operationLocal.copy(
                    isReward = type.isReward,
                    era = type.era,
                    validator = type.validator,
                )
            }
        }
    }
}

fun mapOperationLocalToOperation(operationLocal: OperationLocal, accountName: String?): Operation {
    with(operationLocal) {
        val operation = when (operationType) {
            OperationLocal.OperationType.EXTRINSIC -> Operation.TransactionType.Extrinsic(
                hash = hash,
                module = type!!,
                call = call!!,
                fee = (fee?.toBigDecimal())!!,
                success = success!!
            )
            OperationLocal.OperationType.TRANSFER -> Operation.TransactionType.Transfer(
                amount = (amount?.toBigDecimal())!!,
                receiver = receiver!!,
                sender = sender!!,
                fee = (fee?.toBigDecimal())!!
            )
            OperationLocal.OperationType.REWARD -> Operation.TransactionType.Reward(
                amount = (amount?.toBigDecimal())!!,
                isReward = isReward!!,
                era = era!!,
                validator = validator!!
            )
            else -> throw Exception()
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
        time = node.timestamp.toLong(),
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

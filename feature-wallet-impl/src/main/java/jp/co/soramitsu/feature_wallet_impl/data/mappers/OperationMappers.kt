package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.OperationLocal
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeLocalToTokenType
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeToTokenTypeLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.SubqueryHistoryElementResponse

fun mapOperationStatusToOperationLocalStatus(status: Operation.Status) = when (status) {
    Operation.Status.PENDING -> OperationLocal.Status.PENDING
    Operation.Status.COMPLETED -> OperationLocal.Status.COMPLETED
    Operation.Status.FAILED -> OperationLocal.Status.FAILED
}

fun mapOperationToOperationLocalDb(operation: Operation, source: OperationLocal.Source): OperationLocal {
    with(operation) {
        val amount = type.getOperationAmount()
        val fee = type.getOperationFee()

        return OperationLocal(
            hash = hash,
            address = address,
            time = time * 1000,
            tokenType = mapTokenTypeToTokenTypeLocal(tokenType),
            type = type.header,
            call = type.subheader,
            amount = amount?.toBigInteger(),
            sender = (type as? Operation.Type.Transfer)?.sender,
            receiver = (type as? Operation.Type.Transfer)?.receiver,
            fee = fee?.toBigInteger(),
            isReward = (type as? Operation.Type.Reward)?.isReward,
            era = (type as? Operation.Type.Reward)?.era,
            validator = (type as? Operation.Type.Reward)?.validator,
            success = (type as? Operation.Type.Extrinsic)?.success,
            status = mapOperationStatusToOperationLocalStatus(operation.type.status),
            source = source
        )
    }
}

fun mapOperationLocalToOperation(operationLocal: OperationLocal, accountName: String?): Operation {
    with(operationLocal) {
        val operation = if (type != null && call != null && call != "Staking") {
            Operation.Type.Extrinsic(
                hash = hash,
                module = type!!,
                call = call!!,
                fee = (fee?.toBigDecimal())!!,
                success = success!!
            )
        } else if (call == "Transfer") {
            Operation.Type.Transfer(
                amount = (amount?.toBigDecimal())!!,
                receiver = receiver!!,
                sender = sender!!,
                fee = (fee?.toBigDecimal())!!
            )
        } else {
            Operation.Type.Reward(
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
            type = operation,
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
    val type: Operation.Type?
    when {
        node.reward != null -> {
            type = Operation.Type.Reward(
                amount = token.amountFromPlanks(node.reward.amount.toBigInteger()),
                era = node.reward.era,
                isReward = node.reward.isReward,
                validator = node.reward.validator
            )
        }
        node.extrinsic != null -> {
            type = Operation.Type.Extrinsic(
                hash = node.extrinsic.hash,
                module = node.extrinsic.module,
                call = node.extrinsic.call,
                fee = token.amountFromPlanks(node.extrinsic.fee.toBigInteger()),
                success = node.extrinsic.success
            )
        }
        node.transfer != null -> {
            type = Operation.Type.Transfer(
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
        type = type,
        time = node.timestamp.toLong(),
        tokenType = token,
        accountName = accountName,
        nextPageCursor = cursor
    )
}

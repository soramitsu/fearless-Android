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
        val amount = this.operation.getOperationAmount()
        val fee = this.operation.getOperationFee()

        return OperationLocal(
            hash = hash,
            address = address,
            time = time * 1000,
            tokenType = mapTokenTypeToTokenTypeLocal(tokenType),
            type = this.operation.header,
            call = this.operation.subheader,
            amount = amount?.toBigInteger(),
            sender = (this.operation as? Operation.Operation.Transfer)?.sender,
            receiver = (this.operation as? Operation.Operation.Transfer)?.receiver,
            fee = fee?.toBigInteger(),
            isReward = (this.operation as? Operation.Operation.Reward)?.isReward,
            era = (this.operation as? Operation.Operation.Reward)?.era,
            validator = (this.operation as? Operation.Operation.Reward)?.validator,
            success = (this.operation as? Operation.Operation.Extrinsic)?.success,
            status = mapOperationStatusToOperationLocalStatus(operation.operation.status),
            source = source
        )
    }
}

fun mapOperationLocalToOperation(operationLocal: OperationLocal, accountName: String?): Operation {
    with(operationLocal) {
        val operation = if (type != null && call != null && call != "Staking") {
            Operation.Operation.Extrinsic(
                hash = hash,
                module = type!!,
                call = call!!,
                fee = (fee?.toBigDecimal())!!,
                success = success!!
            )
        } else if (call == "Transfer") {
            Operation.Operation.Transfer(
                amount = (amount?.toBigDecimal())!!,
                receiver = receiver!!,
                sender = sender!!,
                fee = (fee?.toBigDecimal())!!
            )
        } else {
            Operation.Operation.Reward(
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
            operation = operation,
            time = time,
            tokenType = mapTokenTypeLocalToTokenType(tokenType),
        )
    }
}

fun mapNodesToOperation(
    node: SubqueryHistoryElementResponse.Query.HistoryElements.Node,
    cursor: String,
    currentAccount: WalletAccount,
    accountName: String?
): Operation {
    val token = Token.Type.fromNetworkType(currentAccount.network.type)
    val operation: Operation.Operation?
    when {
        node.reward != null -> {
            operation = Operation.Operation.Reward(
                amount = token.amountFromPlanks(node.reward.amount.toBigInteger()),
                era = node.reward.era,
                isReward = node.reward.isReward,
                validator = node.reward.validator
            )
        }
        node.extrinsic != null -> {
            operation = Operation.Operation.Extrinsic(
                hash = node.extrinsic.hash,
                module = node.extrinsic.module,
                call = node.extrinsic.call,
                fee = token.amountFromPlanks(node.extrinsic.fee.toBigInteger()),
                success = node.extrinsic.success
            )
        }
        node.transfer != null -> {
            operation = Operation.Operation.Transfer(
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
        operation = operation,
        time = node.timestamp.toLong(),
        tokenType = token,
        accountName = accountName,
        nextPageCursor = cursor
    )
}

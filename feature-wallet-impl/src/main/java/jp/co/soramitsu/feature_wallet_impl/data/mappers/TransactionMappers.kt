package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.OperationLocal
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeLocalToTokenType
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeToTokenTypeLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.SubqueryElement
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.SubqueryHistoryElementResponse


fun mapSubqueryElementStatusToSubqueryHistoryModelStatus(status: SubqueryElement.Status) = when (status) {
    SubqueryElement.Status.PENDING -> OperationLocal.Status.PENDING
    SubqueryElement.Status.COMPLETED -> OperationLocal.Status.COMPLETED
    SubqueryElement.Status.FAILED -> OperationLocal.Status.FAILED
}

fun mapSubqueryElementToSubqueryHistoryDb(subqueryElement: SubqueryElement, source: OperationLocal.Source): OperationLocal {
    with(subqueryElement) {
        val amount = operation.getOperationAmount()
        val fee = operation.getOperationFee()

        return OperationLocal(
            hash = hash,
            address = address,
            time = time * 1000,
            tokenType = mapTokenTypeToTokenTypeLocal(tokenType),
            type = operation.header,
            call = operation.subheader,
            amount = amount?.toBigInteger(),
            sender = (operation as? SubqueryElement.Operation.Transfer)?.sender,
            receiver = (operation as? SubqueryElement.Operation.Transfer)?.receiver,
            fee = fee?.toBigInteger(),
            isReward = (operation as? SubqueryElement.Operation.Reward)?.isReward,
            era = (operation as? SubqueryElement.Operation.Reward)?.era,
            validator = (operation as? SubqueryElement.Operation.Reward)?.validator,
            success = (operation as? SubqueryElement.Operation.Extrinsic)?.success,
            status = mapSubqueryElementStatusToSubqueryHistoryModelStatus(subqueryElement.operation.status),
            source = source
        )
    }
}

fun mapSubqueryDbToSubqueryElement(operationLocal: OperationLocal, accountName: String?): SubqueryElement {
    with(operationLocal) {
        val operation = if (type != null && call != null && call != "Staking") {
            SubqueryElement.Operation.Extrinsic(
                hash = hash,
                module = type!!,
                call = call!!,
                fee = (fee?.toBigDecimal())!!,
                success = success!!
            )
        } else if (call == "Transfer") {
            SubqueryElement.Operation.Transfer(
                amount = (amount?.toBigDecimal())!!,
                receiver = receiver!!,
                sender = sender!!,
                fee = (fee?.toBigDecimal())!!
            )
        } else {
            SubqueryElement.Operation.Reward(
                amount = (amount?.toBigDecimal())!!,
                isReward = isReward!!,
                era = era!!,
                validator = validator!!
            )
        }

        return SubqueryElement(
            hash = hash,
            address = address,
            accountName = accountName,
            operation = operation,
            time = time,
            tokenType = mapTokenTypeLocalToTokenType(tokenType),
        )
    }
}

fun mapNodesToSubqueryElements(
    node: SubqueryHistoryElementResponse.Query.HistoryElements.Node,
    cursor: String,
    currentAccount: WalletAccount,
    accountName: String?
): SubqueryElement {
    val token = Token.Type.fromNetworkType(currentAccount.network.type)
    val operation: SubqueryElement.Operation?
    when {
        node.reward != null -> {
            operation = SubqueryElement.Operation.Reward(
                amount = token.amountFromPlanks(node.reward.amount.toBigInteger()),
                era = node.reward.era,
                isReward = node.reward.isReward,
                validator = node.reward.validator
            )
        }
        node.extrinsic != null -> {
            operation = SubqueryElement.Operation.Extrinsic(
                hash = node.extrinsic.hash,
                module = node.extrinsic.module,
                call = node.extrinsic.call,
                fee = token.amountFromPlanks(node.extrinsic.fee.toBigInteger()),
                success = node.extrinsic.success
            )
        }
        node.transfer != null -> {///FIXME от меня трансфер
            operation = SubqueryElement.Operation.Transfer(
                amount = token.amountFromPlanks(node.transfer.amount.toBigInteger()),
                receiver = node.transfer.to,
                sender = node.transfer.from,
                fee = token.amountFromPlanks(node.transfer.fee.toBigInteger())
            )
        }
        else -> {
            println("------- EXCEPTION")
            throw Exception()
        }
    }

    return SubqueryElement(
        hash = node.id,
        address = node.address,
        operation = operation,
        time = node.timestamp.toLong(),
        tokenType = token,
        accountName = accountName,
        nextPageCursor = cursor
    )
}

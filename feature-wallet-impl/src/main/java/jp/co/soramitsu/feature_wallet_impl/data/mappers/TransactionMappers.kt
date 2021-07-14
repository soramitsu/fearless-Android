package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.SubqueryHistoryModel
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeLocalToTokenType
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeToTokenTypeLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.RewardSlash
import jp.co.soramitsu.feature_wallet_api.domain.model.Extrinsic
import jp.co.soramitsu.feature_wallet_api.domain.model.HistoryElement
import jp.co.soramitsu.feature_wallet_api.domain.model.NewTransfer
import jp.co.soramitsu.feature_wallet_api.domain.model.SubqueryElement
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.SubqueryHistoryElementResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.TransactionRemote
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel

fun mapTransactionStatusToTransactionStatusLocal(status: Transaction.Status) = when (status) {
    Transaction.Status.PENDING -> TransactionLocal.Status.PENDING
    Transaction.Status.COMPLETED -> TransactionLocal.Status.COMPLETED
    Transaction.Status.FAILED -> TransactionLocal.Status.FAILED
}

fun mapTransactionStatusLocalToTransactionStatus(status: TransactionLocal.Status) = when (status) {
    TransactionLocal.Status.PENDING -> Transaction.Status.PENDING
    TransactionLocal.Status.COMPLETED -> Transaction.Status.COMPLETED
    TransactionLocal.Status.FAILED -> Transaction.Status.FAILED
}

fun mapTransactionLocalToTransaction(transactionLocal: TransactionLocal, accountName: String?): Transaction {
    val tokenType = mapTokenTypeLocalToTokenType(transactionLocal.token)

    return with(transactionLocal) {
        Transaction(
            hash = hash,
            isIncome = recipientAddress == accountAddress,
            recipientAddress = recipientAddress,
            senderAddress = senderAddress,
            amount = amount,
            date = date,
            fee = feeInPlanks?.let(tokenType::amountFromPlanks),
            status = mapTransactionStatusLocalToTransactionStatus(status),
            tokenType = tokenType,
            accountName = accountName
        )
    }
}

fun mapSubqueryElementToSubqueryHistoryDb(subqueryElement: SubqueryElement): SubqueryHistoryModel {
    return SubqueryHistoryModel(
        address = subqueryElement.address,
        operation = subqueryElement.operation,
        amount = subqueryElement.amount.toBigInteger(),
        time = subqueryElement.time,
        tokenType = 0,
        hash = subqueryElement.hash
    )
}

fun mapSubqueryDbToSubqueryElement(subqueryHistoryModel: SubqueryHistoryModel): SubqueryElement {
    return SubqueryElement(
        hash = subqueryHistoryModel.hash,
        address = subqueryHistoryModel.address,
        operation = subqueryHistoryModel.operation,
        amount = subqueryHistoryModel.amount.toBigDecimal(),
        time = subqueryHistoryModel.time,
        tokenType = Token.Type.KSM,
        accountName = null //FIXME
    )
}

fun mapTransactionToTransactionLocal(
    transaction: Transaction,
    accountAddress: String,
    source: TransactionLocal.Source
): TransactionLocal {
    return with(transaction) {
        TransactionLocal(
            accountAddress = accountAddress,
            hash = hash,
            recipientAddress = recipientAddress,
            senderAddress = senderAddress,
            status = mapTransactionStatusToTransactionStatusLocal(status),
            amount = amount,
            date = date,
            source = source,
            token = mapTokenTypeToTokenTypeLocal(tokenType),
            feeInPlanks = fee?.let(tokenType::planksFromAmount),
            networkType = tokenType.networkType
        )
    }
}

fun mapNodesToSubqueryElements(node: SubqueryHistoryElementResponse.Query.HistoryElements.Node, cursor: String, accountName: String?): SubqueryElement {
    val amount = when {
        node.reward != null -> {
            node.reward.amount
        }
        node.extrinsic != null -> {
            node.extrinsic.fee
        }
        node.transfer != null -> {
            node.transfer.amount // TODO maybe amount + fee
        }
        else -> {
            println("------- EXCEPTION")
            throw Exception()
        }
    }

    val operation = when {
        node.reward != null -> {
            "Reward"
        }
        node.extrinsic != null -> {
            "Extrinsic"
        }
        node.transfer != null -> {
            "Transfer"
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
        amount = amount.toBigDecimal(),
        time = node.timestamp.toLong(),
        tokenType = Token.Type.KSM,
        accountName = accountName,
        nextPageCursor = cursor
    )
}

fun mapNodesToTransaction(node: SubqueryHistoryElementResponse.Query.HistoryElements.Node): HistoryElement {
    return when {
        node.reward != null -> {
            RewardSlash(
                amount = node.reward.amount,
                isReward = node.reward.isReward,
                era = node.reward.era,
                validator = node.reward.validator
            )
        }
        node.extrinsic != null -> {
            Extrinsic(
                hash = node.extrinsic.hash,
                module = node.extrinsic.module,
                call = node.extrinsic.call,
                fee = node.extrinsic.fee,
                success = node.extrinsic.success
            )
        }
        node.transfer != null -> {
            NewTransfer(
                amount = node.transfer.amount,
                to = node.transfer.to,
                from = node.transfer.from,
                fee = node.transfer.fee,
                block = node.transfer.block,
                extrinsicId = node.transfer.extrinsicId,
                timestamp = node.timestamp,
                hash = node.id
            )
        }
        else -> {
            throw Exception()
        }
    }
}

fun mapTransferToTransaction(transfer: TransactionRemote, account: WalletAccount, accountName: String?): Transaction {
    val token = Token.Type.fromNetworkType(account.network.type)

    return with(transfer) {
        Transaction(
            hash = hash,
            tokenType = token,
            date = timeInMillis,
            amount = amount,
            status = Transaction.Status.fromSuccess(success),
            senderAddress = from,
            recipientAddress = to,
            fee = token.amountFromPlanks(feeInPlanks),
            isIncome = account.address == to,
            accountName = accountName
        )
    }
}

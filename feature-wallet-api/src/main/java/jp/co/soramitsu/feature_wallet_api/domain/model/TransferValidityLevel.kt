package jp.co.soramitsu.feature_wallet_api.domain.model

interface TransferValidityStatus {
    val level: TransferValidityLevel
}

abstract class BaseStatus(override val level: TransferValidityLevel) : TransferValidityStatus

sealed class TransferValidityLevel(private val level: Int) {
    operator fun compareTo(other: TransferValidityLevel): Int {
        return level - other.level
    }

    object Ok : TransferValidityLevel(level = 0), TransferValidityStatus {
        override val level: TransferValidityLevel = this
    }

    object Warning : TransferValidityLevel(level = 1) {
        sealed class Status : BaseStatus(Warning) {
            object WillRemoveAccount : Status()
        }
    }

    object Error : TransferValidityLevel(level = 2) {
        sealed class Status : BaseStatus(Error) {
            object NotEnoughFunds : Status()

            object DeadRecipient : Error.Status()
        }
    }
}
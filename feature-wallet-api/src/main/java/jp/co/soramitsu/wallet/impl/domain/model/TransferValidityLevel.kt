package jp.co.soramitsu.wallet.impl.domain.model

@Deprecated("Dont use this validation. Use TransferValidationUseCase instead")
interface TransferValidityStatus {
    val level: TransferValidityLevel
}

abstract class BaseStatus(override val level: TransferValidityLevel) : TransferValidityStatus

@Deprecated("Dont use this validation. Use TransferValidationUseCase instead")
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

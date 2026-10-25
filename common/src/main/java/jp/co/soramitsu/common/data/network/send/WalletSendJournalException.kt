package jp.co.soramitsu.common.data.network.send

/** Failure to prove a durable, unique payment must stop signing or submitting another transaction. */
class WalletSendJournalException(val code: Code, val transactionId: String? = null) : IllegalStateException(code.name) {
    enum class Code { STORAGE_UNAVAILABLE, CORRUPT_RECORD, PENDING_PAYMENT, CHANGED_RECORD }
}

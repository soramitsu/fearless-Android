package jp.co.soramitsu.common.data.storage.envelope

/**
 * A sealed wallet secret cannot be opened right now: the wallet is locked,
 * or this session holds another SK. It says nothing about the stored value,
 * which stays exactly as it is.
 *
 * Deliberately not a
 * [jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException]:
 * the start-up gate treats that as a failure of every wallet. Every reader
 * that quarantines, archives or rewrites on a validation failure must let this
 * through unchanged, and a pass that runs before the PIN must leave the value
 * alone and try again after the next unlock.
 */
open class WalletSecretLockedException(
    message: String = "The wallet is locked; its sealed secrets open after the PIN",
    cause: Throwable? = null
) : IllegalStateException(message, cause)

/**
 * A sealed value that this session's SK does not open: a different SK id, a
 * changed byte, or a malformed envelope. Like [WalletSecretLockedException]
 * it is never evidence for quarantine; the value is kept for a later build or
 * a backup proof.
 */
class WalletSecretUnopenableException(message: String, cause: Throwable? = null) :
    WalletSecretLockedException(message, cause)

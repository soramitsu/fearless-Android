package jp.co.soramitsu.account.impl.domain.recovery

import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope

/** Kept in memory only. Never place this object or its encoding in logs or saved UI state. */
class VerifiedWalletSecret internal constructor(val scope: WalletSecretScope, internal val encoded: String) {
    override fun toString(): String = "VerifiedWalletSecret(<redacted>)"
}

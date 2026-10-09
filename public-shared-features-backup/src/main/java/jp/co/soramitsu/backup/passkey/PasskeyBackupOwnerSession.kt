package jp.co.soramitsu.backup.passkey

/** A short-lived owner authority session. The token is deliberately omitted from diagnostics. */
class PasskeyBackupOwnerSession internal constructor(
    @Transient val sessionToken: String,
    val subject: String,
    val namespace: String,
    val generation: Long,
    val platform: String,
    val expiresAt: Long
) {
    override fun toString(): String = "PasskeyBackupOwnerSession([REDACTED])"
}

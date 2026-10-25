package jp.co.soramitsu.common.data.storage.encrypt

/** A short-lived capability issued only after permanent failures across a device restart. */
class WalletKeyLossConsent internal constructor(
    internal val fingerprint: String,
    internal val bootCount: Int,
    internal val issuedAtMillis: Long
) {
    override fun toString(): String = "WalletKeyLossConsent(redacted)"
}

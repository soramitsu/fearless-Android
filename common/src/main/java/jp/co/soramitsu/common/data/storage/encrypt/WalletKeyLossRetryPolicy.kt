package jp.co.soramitsu.common.data.storage.encrypt

/** Failures must refer to the same exact stored key and span two OS boots. */
internal object WalletKeyLossRetryPolicy {
    const val CONSENT_LIFETIME_MILLIS = 5 * 60 * 1000L

    fun mayOffer(firstBoot: Int, currentBoot: Int, failureCount: Int): Boolean =
        firstBoot >= 0 && currentBoot > firstBoot && failureCount >= 2

    fun requireCurrent(consent: WalletKeyLossConsent, fingerprint: String, boot: Int, now: Long) {
        require(consent.fingerprint == fingerprint && consent.bootCount == boot &&
            now >= consent.issuedAtMillis && now - consent.issuedAtMillis <= CONSENT_LIFETIME_MILLIS
        ) { "Secure-storage recovery consent expired or the stored key changed" }
    }
}

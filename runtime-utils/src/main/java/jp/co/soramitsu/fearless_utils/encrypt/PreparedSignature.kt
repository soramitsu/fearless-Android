package jp.co.soramitsu.fearless_utils.encrypt

import java.util.concurrent.atomic.AtomicBoolean

/** One prepared primitive; no provider initialization or payload hashing after authorization. */
class PreparedSignature internal constructor(private val primitive: () -> SignatureWrapper) {
    private val consumed = AtomicBoolean(false)

    /** The authorizer must invoke its action synchronously at most once and retain no action. */
    fun sign(authorize: (() -> SignatureWrapper) -> SignatureWrapper): SignatureWrapper = authorize {
        check(consumed.compareAndSet(false, true)) { "Prepared signature is already consumed" }
        primitive()
    }
}

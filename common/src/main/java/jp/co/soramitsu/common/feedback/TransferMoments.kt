package jp.co.soramitsu.common.feedback

/**
 * Hands a just-submitted transfer from the send flow to the success screen, which draws the solar
 * ignite only for a transfer (the same screen also reports other operations). Holds a time, no data.
 */
object TransferMoments {
    @Volatile
    private var sentAtMs: Long? = null

    /** The network accepted a transfer at [nowMs]. */
    fun markSent(nowMs: Long) {
        sentAtMs = nowMs
    }

    /** True once, for a success screen opened within [windowMs] of a transfer's submission. */
    @Synchronized
    fun consumeRecentSend(nowMs: Long, windowMs: Long = DEFAULT_WINDOW_MS): Boolean {
        val at = sentAtMs ?: return false
        sentAtMs = null
        return nowMs - at in 0..windowMs
    }

    const val DEFAULT_WINDOW_MS = 10_000L
}

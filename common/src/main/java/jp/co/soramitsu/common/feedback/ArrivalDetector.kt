package jp.co.soramitsu.common.feedback

import java.math.BigDecimal

/**
 * Decides when tokens have arrived (brand kit SPEC, "Launch, feel and sound", part 2): an asset's
 * balance for an account grows compared with a value already known for that same account and
 * asset. Never on the first value seen (a new or imported wallet, a first sync, a newly added
 * network), never for a price change (only token amounts are compared), never for a hidden asset,
 * never while the app is not in the foreground, and at most one effect in any [cooldownMs].
 * It only reads balances the wallet already loads; it starts no request of its own.
 */
class ArrivalDetector(private val cooldownMs: Long = DEFAULT_COOLDOWN_MS) {

    /** One asset of one account: its token amount ([amount] null while it is not read yet). */
    data class Holding(val assetKey: String, val amount: BigDecimal?, val hidden: Boolean, val symbol: String = "")

    /** The assets that grew, in the order they were reported. */
    data class Arrival(val assetKeys: List<String>, val symbols: List<String>)

    private val known = HashMap<Long, HashMap<String, BigDecimal>>()
    private var lastEffectAtMs: Long? = null

    /**
     * Records [holdings] of [walletId] and returns the arrival to show, or null. [foreground] is
     * whether the app is in front of the user; in the background values are recorded silently.
     */
    @Synchronized
    fun observe(walletId: Long, holdings: List<Holding>, nowMs: Long, foreground: Boolean): Arrival? {
        val firstSight = walletId !in known
        val previous = known.getOrPut(walletId) { HashMap() }
        val grown = ArrayList<Holding>()
        for (holding in holdings) {
            val amount = holding.amount ?: continue
            val before = previous.put(holding.assetKey, amount)
            if (!firstSight && before != null && !holding.hidden && amount > before) grown += holding
        }
        if (grown.isEmpty() || !foreground) return null
        val last = lastEffectAtMs
        if (last != null && nowMs - last < cooldownMs) return null
        lastEffectAtMs = nowMs
        return Arrival(grown.map { it.assetKey }, grown.map { it.symbol }.filter { it.isNotEmpty() }.distinct())
    }

    /** Forgets everything, as a new process would. */
    @Synchronized
    fun reset() {
        known.clear()
        lastEffectAtMs = null
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 3_000L
    }
}

package jp.co.soramitsu.common.data.network.solana

/** A bounded compute budget; getFeeForMessage remains the authority for the full network fee. */
data class SolanaPriorityFee(val computeUnitLimit: Int, val microLamportsPerUnit: Long) {
    init {
        require(computeUnitLimit in 1..200_000 && microLamportsPerUnit in 1_000..100_000)
    }

    companion object {
        /** Uses the middle recent sample with a small floor and a fixed user-cost ceiling. */
        fun fromSamples(samples: List<Long>, tokenTransfer: Boolean): SolanaPriorityFee {
            require(samples.size <= 256 && samples.all { it >= 0 })
            val ordered = samples.sorted()
            val middle = ordered.getOrNull(ordered.size / 2) ?: 1_000L
            return SolanaPriorityFee(if (tokenTransfer) 200_000 else 10_000, middle.coerceIn(1_000, 100_000))
        }
    }
}

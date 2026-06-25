package jp.co.soramitsu.common.data.network.bitcoin

class BitcoinFeeEstimator(
    private val client: BitcoinIndexerClient
) {
    suspend fun estimate(
        network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        baseUrl: String? = null,
        targetBlocks: Int = DEFAULT_TARGET_BLOCKS
    ): BitcoinFeeEstimate {
        return select(
            estimates = client.feeEstimates(network, baseUrl),
            targetBlocks = targetBlocks
        )
    }

    fun select(
        estimates: Map<String, Double>,
        targetBlocks: Int = DEFAULT_TARGET_BLOCKS
    ): BitcoinFeeEstimate {
        validateTargetBlocks(targetBlocks)
        val entries = estimates.mapNotNull { (blocks, feeRate) ->
            val parsedBlocks = blocks.toIntOrNull()
            if (parsedBlocks != null && parsedBlocks > 0 && feeRate.isFinite() && feeRate > 0.0) {
                parsedBlocks to feeRate
            } else {
                null
            }
        }.sortedBy { it.first }

        if (entries.isEmpty()) {
            throw BitcoinFeeEstimatorException(BitcoinFeeEstimatorException.Code.FEE_ESTIMATES_UNAVAILABLE)
        }

        val selected = entries.firstOrNull { it.first >= targetBlocks } ?: entries.last()
        validateFeeRate(selected.second)

        return BitcoinFeeEstimate(
            requestedTargetBlocks = targetBlocks,
            selectedTargetBlocks = selected.first,
            feeRateSatPerVbyte = selected.second
        )
    }

    private fun validateTargetBlocks(targetBlocks: Int) {
        if (targetBlocks <= 0 || targetBlocks > MAX_TARGET_BLOCKS) {
            throw BitcoinFeeEstimatorException(BitcoinFeeEstimatorException.Code.INVALID_FEE_TARGET)
        }
    }

    private fun validateFeeRate(feeRateSatPerVbyte: Double) {
        if (!feeRateSatPerVbyte.isFinite() || feeRateSatPerVbyte <= 0.0 || feeRateSatPerVbyte > MAX_FEE_RATE_SAT_PER_VBYTE) {
            throw BitcoinFeeEstimatorException(BitcoinFeeEstimatorException.Code.INVALID_FEE_RATE)
        }
    }

    companion object {
        const val DEFAULT_TARGET_BLOCKS = 2
        const val MAX_TARGET_BLOCKS = 1_008
        const val MAX_FEE_RATE_SAT_PER_VBYTE = 10_000.0
    }
}

data class BitcoinFeeEstimate(
    val requestedTargetBlocks: Int,
    val selectedTargetBlocks: Int,
    val feeRateSatPerVbyte: Double
)

class BitcoinFeeEstimatorException(
    val code: Code
) : IllegalArgumentException(code.name) {
    enum class Code {
        INVALID_FEE_TARGET,
        INVALID_FEE_RATE,
        FEE_ESTIMATES_UNAVAILABLE
    }
}

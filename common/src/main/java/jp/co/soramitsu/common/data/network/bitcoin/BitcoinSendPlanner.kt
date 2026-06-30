package jp.co.soramitsu.common.data.network.bitcoin

class BitcoinSendPlanner(
    private val client: BitcoinIndexerClient,
    private val feeEstimator: BitcoinFeeEstimator = BitcoinFeeEstimator(client),
    private val utxoSelector: BitcoinUtxoSelector = BitcoinUtxoSelector()
) {
    suspend fun plan(
        amountSats: Long,
        sources: List<BitcoinUtxoSource>,
        recipientAddress: String,
        changeAddress: String? = null,
        feeRateSatPerVbyte: Double? = null,
        feeTargetBlocks: Int = BitcoinFeeEstimator.DEFAULT_TARGET_BLOCKS,
        includeUnconfirmed: Boolean = false,
        maxInputs: Int = BitcoinUtxoSelector.DEFAULT_MAX_INPUTS,
        network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        baseUrl: String? = null
    ): BitcoinSendPlan {
        val normalizedSources = normalizeSources(sources, network)
        val normalizedRecipient = normalizeAddress(
            recipientAddress,
            network,
            BitcoinSendPlannerException.Code.INVALID_RECIPIENT_ADDRESS
        )
        val normalizedChange = normalizeAddress(
            changeAddress ?: normalizedSources.first().address,
            network,
            BitcoinSendPlannerException.Code.INVALID_CHANGE_ADDRESS
        )
        val resolvedFeeRate = feeRateSatPerVbyte ?: feeEstimator.estimate(
            network = network,
            baseUrl = baseUrl,
            targetBlocks = feeTargetBlocks
        ).feeRateSatPerVbyte
        validateFeeRate(resolvedFeeRate)

        val spendableUtxos = normalizedSources.flatMap { source ->
            utxoSelector.spendableUtxos(
                source = source,
                utxos = client.utxos(source.address, network, baseUrl),
                network = network,
                includeUnconfirmed = includeUnconfirmed
            )
        }

        if (spendableUtxos.isEmpty()) {
            throw BitcoinSendPlannerException(BitcoinSendPlannerException.Code.NO_SPENDABLE_UTXOS)
        }

        val selection = utxoSelector.select(
            amountSats = amountSats,
            changeAddress = normalizedChange,
            feeRateSatPerVbyte = resolvedFeeRate,
            maxInputs = maxInputs,
            network = network,
            utxos = spendableUtxos
        )

        return BitcoinSendPlan(
            amountSats = amountSats,
            recipientAddress = normalizedRecipient,
            changeAddress = selection.changeAddress,
            feeRateSatPerVbyte = resolvedFeeRate,
            feeTargetBlocks = if (feeRateSatPerVbyte == null) feeTargetBlocks else null,
            includeUnconfirmed = includeUnconfirmed,
            network = network,
            sourceAddresses = normalizedSources.map { it.address },
            selectedUtxos = selection.selectedUtxos,
            inputTotalSats = selection.inputTotalSats,
            feeSats = selection.feeSats,
            changeSats = selection.changeSats,
            absorbedDustSats = selection.absorbedDustSats
        )
    }

    private fun normalizeSources(
        sources: List<BitcoinUtxoSource>,
        network: BitcoinIndexerRoutes.Network
    ): List<BitcoinUtxoSource> {
        if (sources.isEmpty()) {
            throw BitcoinSendPlannerException(BitcoinSendPlannerException.Code.SOURCES_REQUIRED)
        }
        if (sources.size > MAX_SOURCES) {
            throw BitcoinSendPlannerException(BitcoinSendPlannerException.Code.TOO_MANY_SOURCES)
        }
        val seen = mutableSetOf<String>()

        return sources.map { source ->
            val address = normalizeAddress(
                source.address,
                network,
                BitcoinSendPlannerException.Code.INVALID_SOURCE_ADDRESS
            )
            source.derivationPath?.let {
                if (!DERIVATION_PATH.matches(it)) {
                    throw BitcoinSendPlannerException(BitcoinSendPlannerException.Code.INVALID_DERIVATION_PATH)
                }
            }

            if (!seen.add(address.lowercase())) {
                throw BitcoinSendPlannerException(BitcoinSendPlannerException.Code.DUPLICATE_SOURCE_ADDRESS)
            }

            BitcoinUtxoSource(address = address, derivationPath = source.derivationPath)
        }
    }

    private fun normalizeAddress(
        address: String,
        network: BitcoinIndexerRoutes.Network,
        code: BitcoinSendPlannerException.Code
    ): String {
        return try {
            BitcoinIndexerRoutes.normalizeAddress(address, network)
        } catch (_: BitcoinIndexerRoutes.BitcoinIndexerRouteException) {
            throw BitcoinSendPlannerException(code)
        }
    }

    private fun validateFeeRate(feeRateSatPerVbyte: Double) {
        if (!feeRateSatPerVbyte.isFinite() || feeRateSatPerVbyte <= 0.0 || feeRateSatPerVbyte > BitcoinFeeEstimator.MAX_FEE_RATE_SAT_PER_VBYTE) {
            throw BitcoinSendPlannerException(BitcoinSendPlannerException.Code.INVALID_FEE_RATE)
        }
    }

    private companion object {
        const val MAX_SOURCES = 100
        val DERIVATION_PATH = Regex("^m(?:/\\d+'?)+$")
    }
}

data class BitcoinSendPlan(
    val amountSats: Long,
    val recipientAddress: String,
    val changeAddress: String?,
    val feeRateSatPerVbyte: Double,
    val feeTargetBlocks: Int?,
    val includeUnconfirmed: Boolean,
    val network: BitcoinIndexerRoutes.Network,
    val sourceAddresses: List<String>,
    val selectedUtxos: List<BitcoinSpendableUtxo>,
    val inputTotalSats: Long,
    val feeSats: Long,
    val changeSats: Long,
    val absorbedDustSats: Long
)

class BitcoinSendPlannerException(
    val code: Code
) : IllegalArgumentException(code.name) {
    enum class Code {
        DUPLICATE_SOURCE_ADDRESS,
        INVALID_CHANGE_ADDRESS,
        INVALID_DERIVATION_PATH,
        INVALID_FEE_RATE,
        INVALID_RECIPIENT_ADDRESS,
        INVALID_SOURCE_ADDRESS,
        NO_SPENDABLE_UTXOS,
        SOURCES_REQUIRED,
        TOO_MANY_SOURCES
    }
}

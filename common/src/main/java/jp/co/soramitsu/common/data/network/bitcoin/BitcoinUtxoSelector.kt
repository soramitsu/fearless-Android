package jp.co.soramitsu.common.data.network.bitcoin

import java.lang.Math.addExact
import kotlin.math.ceil

class BitcoinUtxoSelector {
    fun spendableUtxos(
        source: BitcoinUtxoSource,
        utxos: List<BitcoinEsploraUtxo>,
        network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        includeUnconfirmed: Boolean = false
    ): List<BitcoinSpendableUtxo> {
        val address = try {
            BitcoinIndexerRoutes.normalizeAddress(source.address, network)
        } catch (_: BitcoinIndexerRoutes.BitcoinIndexerRouteException) {
            throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INVALID_SOURCE_ADDRESS)
        }
        source.derivationPath?.let(::validateDerivationPath)

        return utxos
            .filter { includeUnconfirmed || it.status.confirmed }
            .map {
                BitcoinSpendableUtxo(
                    address = address,
                    derivationPath = source.derivationPath,
                    txid = it.txid,
                    valueSats = it.value,
                    vout = it.vout
                )
            }
    }

    fun select(
        amountSats: Long,
        changeAddress: String,
        feeRateSatPerVbyte: Double,
        maxInputs: Int = DEFAULT_MAX_INPUTS,
        network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        utxos: List<BitcoinSpendableUtxo>
    ): BitcoinUtxoSelectionResult {
        validateAmount(amountSats)
        val normalizedChangeAddress = try {
            BitcoinIndexerRoutes.normalizeAddress(changeAddress, network)
        } catch (_: BitcoinIndexerRoutes.BitcoinIndexerRouteException) {
            throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INVALID_CHANGE_ADDRESS)
        }
        validateFeeRate(feeRateSatPerVbyte)
        validateMaxInputs(maxInputs)
        val normalizedUtxos = normalizeUtxos(utxos)
        val selected = mutableListOf<BitcoinSpendableUtxo>()
        var total = 0L

        for (utxo in normalizedUtxos.sortedWith(UTXO_ORDER)) {
            selected += utxo
            if (selected.size > maxInputs) {
                throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.TOO_MANY_INPUTS_REQUIRED)
            }
            total = safeAdd(total, utxo.valueSats)
            if (total > MAX_SATOSHI) {
                throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.SATOSHI_OVERFLOW)
            }

            val noChangeFee = estimateFee(selected.size, outputCount = 1, feeRateSatPerVbyte)
            val noChangeRemainder = total - amountSats - noChangeFee

            if (noChangeRemainder == 0L) {
                return BitcoinUtxoSelectionResult(
                    selectedUtxos = selected.toList(),
                    inputTotalSats = total,
                    feeSats = noChangeFee,
                    changeAddress = null,
                    changeSats = 0,
                    absorbedDustSats = 0
                )
            }

            if (noChangeRemainder > 0 && noChangeRemainder < BITCOIN_P2WPKH_DUST_SATS) {
                return BitcoinUtxoSelectionResult(
                    selectedUtxos = selected.toList(),
                    inputTotalSats = total,
                    feeSats = safeAdd(noChangeFee, noChangeRemainder),
                    changeAddress = null,
                    changeSats = 0,
                    absorbedDustSats = noChangeRemainder
                )
            }

            val withChangeFee = estimateFee(selected.size, outputCount = 2, feeRateSatPerVbyte)
            val change = total - amountSats - withChangeFee

            if (change >= BITCOIN_P2WPKH_DUST_SATS) {
                return BitcoinUtxoSelectionResult(
                    selectedUtxos = selected.toList(),
                    inputTotalSats = total,
                    feeSats = withChangeFee,
                    changeAddress = normalizedChangeAddress,
                    changeSats = change,
                    absorbedDustSats = 0
                )
            }
        }

        throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INSUFFICIENT_FUNDS)
    }

    fun estimateP2wpkhTransactionVSize(inputCount: Int, outputCount: Int): Int {
        if (inputCount <= 0) {
            throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INVALID_INPUT_COUNT)
        }
        if (outputCount <= 0) {
            throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INVALID_OUTPUT_COUNT)
        }

        return TX_OVERHEAD_VBYTES + inputCount * P2WPKH_INPUT_VBYTES + outputCount * P2WPKH_OUTPUT_VBYTES
    }

    private fun normalizeUtxos(utxos: List<BitcoinSpendableUtxo>): List<BitcoinSpendableUtxo> {
        if (utxos.isEmpty()) {
            throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.UTXOS_REQUIRED)
        }
        val outpoints = mutableSetOf<String>()

        return utxos.map { utxo ->
            val normalizedTxid = utxo.txid.trim().lowercase()
            if (!TXID.matches(normalizedTxid)) {
                throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INVALID_TXID)
            }
            if (utxo.vout !in 0..MAX_UINT32) {
                throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INVALID_VOUT)
            }
            if (utxo.valueSats <= 0 || utxo.valueSats > MAX_SATOSHI) {
                throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INVALID_UTXO_VALUE)
            }
            utxo.scriptPubKey?.let(::validateScriptPubKey)
            utxo.derivationPath?.let(::validateDerivationPath)

            val outpoint = "$normalizedTxid:${utxo.vout}"
            if (!outpoints.add(outpoint)) {
                throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.DUPLICATE_UTXO)
            }

            utxo.copy(txid = normalizedTxid)
        }
    }

    private fun validateAmount(amountSats: Long) {
        if (amountSats <= 0 || amountSats > MAX_SATOSHI) {
            throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INVALID_AMOUNT)
        }
        if (amountSats < BITCOIN_P2WPKH_DUST_SATS) {
            throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.AMOUNT_BELOW_DUST)
        }
    }

    private fun validateFeeRate(feeRateSatPerVbyte: Double) {
        if (!feeRateSatPerVbyte.isFinite() || feeRateSatPerVbyte <= 0.0 || feeRateSatPerVbyte > MAX_FEE_RATE_SAT_PER_VBYTE) {
            throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INVALID_FEE_RATE)
        }
    }

    private fun validateMaxInputs(maxInputs: Int) {
        if (maxInputs <= 0 || maxInputs > DEFAULT_MAX_INPUTS) {
            throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INVALID_MAX_INPUTS)
        }
    }

    private fun validateScriptPubKey(scriptPubKey: String) {
        if (!SCRIPT_PUBKEY.matches(scriptPubKey.trim())) {
            throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INVALID_SCRIPT_PUBKEY)
        }
    }

    private fun validateDerivationPath(derivationPath: String) {
        if (!DERIVATION_PATH.matches(derivationPath)) {
            throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INVALID_DERIVATION_PATH)
        }
    }

    private fun estimateFee(inputCount: Int, outputCount: Int, feeRateSatPerVbyte: Double): Long {
        val fee = ceil(estimateP2wpkhTransactionVSize(inputCount, outputCount) * feeRateSatPerVbyte)
        if (!fee.isFinite() || fee <= 0 || fee > Long.MAX_VALUE) {
            throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.INVALID_FEE_RATE)
        }

        return fee.toLong()
    }

    private fun safeAdd(left: Long, right: Long): Long {
        return try {
            addExact(left, right)
        } catch (_: ArithmeticException) {
            throw BitcoinUtxoSelectionException(BitcoinUtxoSelectionException.Code.SATOSHI_OVERFLOW)
        }
    }

    companion object {
        const val BITCOIN_P2WPKH_DUST_SATS = 330L
        const val DEFAULT_MAX_INPUTS = 100
        const val MAX_FEE_RATE_SAT_PER_VBYTE = 10_000.0
        const val MAX_SATOSHI = 2_100_000_000_000_000L
        private const val MAX_UINT32 = 0xffffffffL
        private const val P2WPKH_INPUT_VBYTES = 68
        private const val P2WPKH_OUTPUT_VBYTES = 31
        private const val TX_OVERHEAD_VBYTES = 11

        private val DERIVATION_PATH = Regex("^m(?:/\\d+'?)+$")
        private val SCRIPT_PUBKEY = Regex("^(?:[0-9a-fA-F]{2})+$")
        private val TXID = Regex("^[0-9a-f]{64}$", RegexOption.IGNORE_CASE)
        private val UTXO_ORDER = compareByDescending<BitcoinSpendableUtxo> { it.valueSats }
            .thenBy { it.txid.lowercase() }
            .thenBy { it.vout }
    }
}

data class BitcoinUtxoSource(
    val address: String,
    val derivationPath: String? = null
)

data class BitcoinSpendableUtxo(
    val address: String? = null,
    val derivationPath: String? = null,
    val scriptPubKey: String? = null,
    val txid: String,
    val valueSats: Long,
    val vout: Long
)

data class BitcoinUtxoSelectionResult(
    val selectedUtxos: List<BitcoinSpendableUtxo>,
    val inputTotalSats: Long,
    val feeSats: Long,
    val changeAddress: String?,
    val changeSats: Long,
    val absorbedDustSats: Long
)

class BitcoinUtxoSelectionException(
    val code: Code
) : IllegalArgumentException(code.name) {
    enum class Code {
        AMOUNT_BELOW_DUST,
        DUPLICATE_UTXO,
        INSUFFICIENT_FUNDS,
        INVALID_AMOUNT,
        INVALID_CHANGE_ADDRESS,
        INVALID_DERIVATION_PATH,
        INVALID_FEE_RATE,
        INVALID_INPUT_COUNT,
        INVALID_MAX_INPUTS,
        INVALID_OUTPUT_COUNT,
        INVALID_SCRIPT_PUBKEY,
        INVALID_SOURCE_ADDRESS,
        INVALID_TXID,
        INVALID_UTXO_VALUE,
        INVALID_VOUT,
        SATOSHI_OVERFLOW,
        TOO_MANY_INPUTS_REQUIRED,
        UTXOS_REQUIRED
    }
}

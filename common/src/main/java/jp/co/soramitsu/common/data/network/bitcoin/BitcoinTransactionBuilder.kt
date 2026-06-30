package jp.co.soramitsu.common.data.network.bitcoin

import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.math.ceil

object BitcoinTransactionBuilder {
    private const val DEFAULT_SEQUENCE = 0xffffffffL
    private const val SIGHASH_ALL = 0x01L
    private const val TX_LOCKTIME = 0L
    private const val TX_VERSION = 2L
    private const val MAX_UINT32 = 0xffffffffL

    private val SECP256K1 = CustomNamedCurves.getByName("secp256k1")
    private val DOMAIN = ECDomainParameters(SECP256K1.curve, SECP256K1.g, SECP256K1.n, SECP256K1.h)
    private val HALF_CURVE_ORDER = SECP256K1.n.shiftRight(1)
    private val BECH32_ALPHABET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val BECH32_GENERATORS = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    fun buildP2wpkhTransaction(
        mnemonic: String,
        passphrase: String = "",
        inputs: List<BitcoinSpendableUtxo>,
        outputs: List<BitcoinPaymentOutput>,
        changeAddress: String? = null,
        feeRateSatPerVbyte: Double? = null,
        feeSats: Long? = null,
        network: BitcoinKeyDerivation.Network = BitcoinKeyDerivation.Network.Mainnet
    ): BitcoinBuiltTransaction {
        val normalizedInputs = normalizeInputs(inputs)
        val normalizedOutputs = normalizeOutputs(outputs, network)
        val inputTotal = sumSats(normalizedInputs.map { it.valueSats })
        val outputTotal = sumSats(normalizedOutputs.map { it.valueSats })
        val fee = normalizeFee(
            feeSats = feeSats,
            feeRateSatPerVbyte = feeRateSatPerVbyte,
            inputCount = normalizedInputs.size,
            outputCount = normalizedOutputs.size + if (changeAddress != null) 1 else 0
        )
        val change = inputTotal - outputTotal - fee

        if (change < 0) {
            throw BitcoinTransactionException(BitcoinTransactionException.Code.INSUFFICIENT_FUNDS)
        }

        val finalOutputs = normalizedOutputs.toMutableList()
        if (change > 0) {
            val normalizedChangeAddress = changeAddress ?: throw BitcoinTransactionException(BitcoinTransactionException.Code.CHANGE_ADDRESS_REQUIRED)
            if (change < BitcoinUtxoSelector.BITCOIN_P2WPKH_DUST_SATS) {
                throw BitcoinTransactionException(BitcoinTransactionException.Code.CHANGE_BELOW_DUST)
            }
            finalOutputs += normalizeOutput(BitcoinPaymentOutput(normalizedChangeAddress, change), network)
        }

        val normalizedMnemonic = normalizeMnemonic(mnemonic)
        val signingInputs = normalizedInputs.map { input ->
            val key = try {
                BitcoinKeyDerivation.deriveKey(
                    mnemonic = normalizedMnemonic,
                    passphrase = passphrase,
                    derivationPath = input.derivationPath ?: BitcoinKeyDerivation.getReceivePath(network),
                    network = network
                )
            } catch (_: IllegalArgumentException) {
                throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_DERIVATION_PATH)
            }
            val witnessScript = p2wpkhOutputScript(key.publicKey)

            if (input.address != null && input.address.lowercase() != key.address.lowercase()) {
                throw BitcoinTransactionException(BitcoinTransactionException.Code.UTXO_ADDRESS_MISMATCH)
            }
            if (input.scriptPubKey != null && input.scriptPubKey.lowercase() != witnessScript.toHex()) {
                throw BitcoinTransactionException(BitcoinTransactionException.Code.UTXO_SCRIPT_MISMATCH)
            }

            BitcoinSigningInput(
                address = input.address,
                derivationPath = input.derivationPath,
                scriptPubKey = input.scriptPubKey,
                txid = input.txid,
                valueSats = input.valueSats,
                vout = input.vout,
                privateKey = key.privateKey,
                publicKey = key.publicKey,
                scriptCode = p2wpkhScriptCode(key.publicKey),
                witnessScript = witnessScript
            )
        }
        val serializedOutputs = finalOutputs.map { output ->
            BitcoinSerializedOutput(script = p2wpkhOutputScriptFromAddress(output.address), valueSats = output.valueSats)
        }
        val witnesses = signingInputs.map { signP2wpkhInput(signingInputs, serializedOutputs, it) }
        val baseTx = serializeTransaction(signingInputs, serializedOutputs)
        val witnessTx = serializeTransaction(signingInputs, serializedOutputs, witnesses)

        return BitcoinBuiltTransaction(
            changeSats = change,
            feeSats = fee,
            inputTotalSats = inputTotal,
            outputTotalSats = outputTotal,
            txHex = witnessTx.toHex(),
            txid = doubleSha256(baseTx).reversedArray().toHex(),
            vsize = ceil((baseTx.size * 3 + witnessTx.size) / 4.0).toInt()
        )
    }

    fun estimateP2wpkhTransactionVSize(inputCount: Int, outputCount: Int): Int {
        return BitcoinUtxoSelector().estimateP2wpkhTransactionVSize(inputCount, outputCount)
    }

    private fun normalizeInputs(inputs: List<BitcoinSpendableUtxo>): List<BitcoinSpendableUtxo> {
        if (inputs.isEmpty()) {
            throw BitcoinTransactionException(BitcoinTransactionException.Code.INPUTS_REQUIRED)
        }
        val outpoints = mutableSetOf<String>()

        return inputs.map { input ->
            val normalizedTxid = input.txid.trim().lowercase()
            if (!TXID.matches(normalizedTxid)) {
                throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_TXID)
            }
            if (input.vout !in 0..MAX_UINT32) {
                throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_VOUT)
            }
            if (input.valueSats <= 0 || input.valueSats > BitcoinUtxoSelector.MAX_SATOSHI) {
                throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_UTXO_VALUE)
            }
            input.scriptPubKey?.let {
                if (!SCRIPT_PUBKEY.matches(it)) {
                    throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_SCRIPT_PUBKEY)
                }
            }
            input.derivationPath?.let {
                if (!DERIVATION_PATH.matches(it)) {
                    throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_DERIVATION_PATH)
                }
            }
            val outpoint = "$normalizedTxid:${input.vout}"
            if (!outpoints.add(outpoint)) {
                throw BitcoinTransactionException(BitcoinTransactionException.Code.DUPLICATE_UTXO)
            }

            input.copy(txid = normalizedTxid)
        }
    }

    private fun normalizeMnemonic(mnemonic: String): String {
        val words = mnemonic.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) {
            throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_MNEMONIC)
        }

        return words.joinToString(" ")
    }

    private fun normalizeOutputs(outputs: List<BitcoinPaymentOutput>, network: BitcoinKeyDerivation.Network): List<BitcoinPaymentOutput> {
        if (outputs.isEmpty()) {
            throw BitcoinTransactionException(BitcoinTransactionException.Code.OUTPUTS_REQUIRED)
        }

        return outputs.map { normalizeOutput(it, network) }
    }

    private fun normalizeOutput(output: BitcoinPaymentOutput, network: BitcoinKeyDerivation.Network): BitcoinPaymentOutput {
        val indexerNetwork = network.toIndexerNetwork()
        val normalizedAddress = try {
            BitcoinIndexerRoutes.normalizeAddress(output.address, indexerNetwork)
        } catch (_: BitcoinIndexerRoutes.BitcoinIndexerRouteException) {
            throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_OUTPUT_ADDRESS)
        }
        if (output.valueSats < BitcoinUtxoSelector.BITCOIN_P2WPKH_DUST_SATS || output.valueSats > BitcoinUtxoSelector.MAX_SATOSHI) {
            throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_OUTPUT_VALUE)
        }

        return BitcoinPaymentOutput(normalizedAddress, output.valueSats)
    }

    private fun normalizeFee(
        feeSats: Long?,
        feeRateSatPerVbyte: Double?,
        inputCount: Int,
        outputCount: Int
    ): Long {
        feeSats?.let {
            if (it <= 0 || it > BitcoinUtxoSelector.MAX_SATOSHI) {
                throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_FEE)
            }

            return it
        }

        val feeRate = feeRateSatPerVbyte ?: throw BitcoinTransactionException(BitcoinTransactionException.Code.FEE_REQUIRED)
        if (!feeRate.isFinite() || feeRate <= 0.0 || feeRate > BitcoinUtxoSelector.MAX_FEE_RATE_SAT_PER_VBYTE) {
            throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_FEE_RATE)
        }

        return ceil(estimateP2wpkhTransactionVSize(inputCount, outputCount) * feeRate).toLong()
    }

    private fun signP2wpkhInput(
        inputs: List<BitcoinSigningInput>,
        outputs: List<BitcoinSerializedOutput>,
        input: BitcoinSigningInput
    ): List<ByteArray> {
        val sighash = bip143SignatureHash(inputs, outputs, input)
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(BigInteger(1, input.privateKey), DOMAIN))
        val components = signer.generateSignature(sighash)
        val r = components[0]
        val s = if (components[1] > HALF_CURVE_ORDER) SECP256K1.n.subtract(components[1]) else components[1]
        val signature = derSignature(r, s) + byteArrayOf(SIGHASH_ALL.toByte())

        return listOf(signature, input.publicKey)
    }

    private fun bip143SignatureHash(
        inputs: List<BitcoinSigningInput>,
        outputs: List<BitcoinSerializedOutput>,
        input: BitcoinSigningInput
    ): ByteArray {
        return doubleSha256(
            concat(
                uint32LE(TX_VERSION),
                doubleSha256(concat(inputs.map { serializeOutpoint(it) })),
                doubleSha256(concat(inputs.map { uint32LE(DEFAULT_SEQUENCE) })),
                serializeOutpoint(input),
                varSlice(input.scriptCode),
                uint64LE(input.valueSats),
                uint32LE(DEFAULT_SEQUENCE),
                doubleSha256(concat(outputs.map { serializeOutput(it) })),
                uint32LE(TX_LOCKTIME),
                uint32LE(SIGHASH_ALL)
            )
        )
    }

    private fun serializeTransaction(
        inputs: List<BitcoinSigningInput>,
        outputs: List<BitcoinSerializedOutput>,
        witnesses: List<List<ByteArray>>? = null
    ): ByteArray {
        return concat(
            listOfNotNull(
                uint32LE(TX_VERSION),
                witnesses?.let { byteArrayOf(0x00, 0x01) },
                varInt(inputs.size.toLong()),
                concat(inputs.map { serializeInput(it) }),
                varInt(outputs.size.toLong()),
                concat(outputs.map { serializeOutput(it) }),
                witnesses?.let { concat(it.map(::serializeWitness)) },
                uint32LE(TX_LOCKTIME)
            )
        )
    }

    private fun serializeInput(input: BitcoinSigningInput): ByteArray {
        return concat(serializeOutpoint(input), varSlice(ByteArray(0)), uint32LE(DEFAULT_SEQUENCE))
    }

    private fun serializeOutpoint(input: BitcoinSigningInput): ByteArray {
        return concat(input.txid.hexToBytes().reversedArray(), uint32LE(input.vout))
    }

    private fun serializeOutput(output: BitcoinSerializedOutput): ByteArray {
        return concat(uint64LE(output.valueSats), varSlice(output.script))
    }

    private fun serializeWitness(witness: List<ByteArray>): ByteArray {
        return concat(varInt(witness.size.toLong()), concat(witness.map(::varSlice)))
    }

    private fun p2wpkhOutputScript(publicKey: ByteArray): ByteArray {
        return concat(byteArrayOf(0x00, 0x14), hash160(publicKey))
    }

    private fun p2wpkhScriptCode(publicKey: ByteArray): ByteArray {
        return concat(byteArrayOf(0x76, 0xa9.toByte(), 0x14), hash160(publicKey), byteArrayOf(0x88.toByte(), 0xac.toByte()))
    }

    private fun p2wpkhOutputScriptFromAddress(address: String): ByteArray {
        val decoded = bech32Decode(address)
        val program = convertBits(decoded.words.drop(1), fromBits = 5, toBits = 8, pad = false)
            .map { it.toByte() }
            .toByteArray()

        if (decoded.words.firstOrNull() != 0 || program.size != 20) {
            throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_OUTPUT_ADDRESS)
        }

        return concat(byteArrayOf(0x00, 0x14), program)
    }

    private fun derSignature(r: BigInteger, s: BigInteger): ByteArray {
        val encodedR = derInteger(r)
        val encodedS = derInteger(s)

        return concat(byteArrayOf(0x30, (encodedR.size + encodedS.size).toByte()), encodedR, encodedS)
    }

    private fun derInteger(value: BigInteger): ByteArray {
        val stripped = value.toUnsignedBytes().dropWhile { it == 0.toByte() }
        val raw = (stripped.ifEmpty { listOf(0.toByte()) }).toByteArray()

        return if ((raw[0].toInt() and 0x80) != 0) {
            concat(byteArrayOf(0x02, (raw.size + 1).toByte(), 0x00), raw)
        } else {
            concat(byteArrayOf(0x02, raw.size.toByte()), raw)
        }
    }

    private fun bech32Decode(address: String): Bech32Decoded {
        if (address != address.lowercase() && address != address.uppercase()) {
            throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_OUTPUT_ADDRESS)
        }
        val normalized = address.lowercase()
        val separator = normalized.lastIndexOf('1')
        if (separator <= 0 || separator + 7 > normalized.length) {
            throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_OUTPUT_ADDRESS)
        }
        val hrp = normalized.substring(0, separator)
        val values = normalized.substring(separator + 1).map { char ->
            val index = BECH32_ALPHABET.indexOf(char)
            if (index < 0) {
                throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_OUTPUT_ADDRESS)
            }
            index
        }
        if (bech32Polymod(expandHrp(hrp) + values) != 1) {
            throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_OUTPUT_ADDRESS)
        }

        return Bech32Decoded(hrp, values.dropLast(6))
    }

    private fun bech32Polymod(values: List<Int>): Int {
        var checksum = 1

        values.forEach { value ->
            val top = checksum shr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor value
            BECH32_GENERATORS.forEachIndexed { index, generator ->
                if (((top shr index) and 1) == 1) {
                    checksum = checksum xor generator
                }
            }
        }

        return checksum
    }

    private fun expandHrp(hrp: String): List<Int> = hrp.map { it.code shr 5 } + listOf(0) + hrp.map { it.code and 31 }

    private fun convertBits(values: List<Int>, fromBits: Int, toBits: Int, pad: Boolean): List<Int> {
        var accumulator = 0
        var bits = 0
        val maxValue = (1 shl toBits) - 1
        val maxAccumulator = (1 shl (fromBits + toBits - 1)) - 1
        val result = mutableListOf<Int>()

        values.forEach { value ->
            if (value < 0 || value ushr fromBits != 0) {
                throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_OUTPUT_ADDRESS)
            }
            accumulator = ((accumulator shl fromBits) or value) and maxAccumulator
            bits += fromBits

            while (bits >= toBits) {
                bits -= toBits
                result += (accumulator shr bits) and maxValue
            }
        }

        if (pad) {
            if (bits > 0) {
                result += (accumulator shl (toBits - bits)) and maxValue
            }
        } else if (bits >= fromBits || ((accumulator shl (toBits - bits)) and maxValue) != 0) {
            throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_OUTPUT_ADDRESS)
        }

        return result
    }

    private fun hash160(value: ByteArray): ByteArray {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(value)
        val digest = RIPEMD160Digest()
        digest.update(sha256, 0, sha256.size)
        val output = ByteArray(20)
        digest.doFinal(output, 0)

        return output
    }

    private fun doubleSha256(value: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        return sha.digest(sha.digest(value))
    }

    private fun varSlice(value: ByteArray): ByteArray = concat(varInt(value.size.toLong()), value)

    private fun varInt(value: Long): ByteArray {
        if (value < 0) {
            throw BitcoinTransactionException(BitcoinTransactionException.Code.INVALID_VARINT)
        }
        return when {
            value < 0xfd -> byteArrayOf(value.toByte())
            value <= 0xffff -> concat(byteArrayOf(0xfd.toByte()), uint16LE(value))
            value <= 0xffffffffL -> concat(byteArrayOf(0xfe.toByte()), uint32LE(value))
            else -> concat(byteArrayOf(0xff.toByte()), uint64LE(value))
        }
    }

    private fun uint16LE(value: Long): ByteArray = byteArrayOf((value and 0xff).toByte(), ((value ushr 8) and 0xff).toByte())

    private fun uint32LE(value: Long): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        ((value ushr 8) and 0xff).toByte(),
        ((value ushr 16) and 0xff).toByte(),
        ((value ushr 24) and 0xff).toByte()
    )

    private fun uint64LE(value: Long): ByteArray {
        var remaining = value
        val output = ByteArray(8)
        for (index in output.indices) {
            output[index] = (remaining and 0xff).toByte()
            remaining = remaining ushr 8
        }

        return output
    }

    private fun sumSats(values: List<Long>): Long {
        return values.fold(0L) { total, value ->
            val next = try {
                Math.addExact(total, value)
            } catch (_: ArithmeticException) {
                throw BitcoinTransactionException(BitcoinTransactionException.Code.SATOSHI_OVERFLOW)
            }
            if (next > BitcoinUtxoSelector.MAX_SATOSHI) {
                throw BitcoinTransactionException(BitcoinTransactionException.Code.SATOSHI_OVERFLOW)
            }

            next
        }
    }

    private fun BitcoinKeyDerivation.Network.toIndexerNetwork(): BitcoinIndexerRoutes.Network = when (this) {
        BitcoinKeyDerivation.Network.Mainnet -> BitcoinIndexerRoutes.Network.Mainnet
        BitcoinKeyDerivation.Network.Testnet -> BitcoinIndexerRoutes.Network.Testnet
    }

    private fun concat(vararg chunks: ByteArray): ByteArray = concat(chunks.toList())

    private fun concat(chunks: List<ByteArray>): ByteArray {
        val output = ByteArrayOutputStream(chunks.sumOf { it.size })
        chunks.forEach { output.write(it) }
        return output.toByteArray()
    }

    private fun String.hexToBytes(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun BigInteger.toUnsignedBytes(): ByteArray {
        val raw = toByteArray()
        return if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
    }

    private val TXID = Regex("^[0-9a-f]{64}$", RegexOption.IGNORE_CASE)
    private val SCRIPT_PUBKEY = Regex("^(?:[0-9a-fA-F]{2})+$")
    private val DERIVATION_PATH = Regex("^m(?:/\\d+'?)+$")
}

data class BitcoinPaymentOutput(
    val address: String,
    val valueSats: Long
)

data class BitcoinBuiltTransaction(
    val changeSats: Long,
    val feeSats: Long,
    val inputTotalSats: Long,
    val outputTotalSats: Long,
    val txHex: String,
    val txid: String,
    val vsize: Int
)

class BitcoinTransactionException(
    val code: Code
) : IllegalArgumentException(code.name) {
    enum class Code {
        CHANGE_ADDRESS_REQUIRED,
        CHANGE_BELOW_DUST,
        DUPLICATE_UTXO,
        FEE_REQUIRED,
        INPUTS_REQUIRED,
        INSUFFICIENT_FUNDS,
        INVALID_DERIVATION_PATH,
        INVALID_FEE,
        INVALID_FEE_RATE,
        INVALID_MNEMONIC,
        INVALID_OUTPUT_ADDRESS,
        INVALID_OUTPUT_VALUE,
        INVALID_SCRIPT_PUBKEY,
        INVALID_TXID,
        INVALID_UTXO_VALUE,
        INVALID_VARINT,
        INVALID_VOUT,
        OUTPUTS_REQUIRED,
        SATOSHI_OVERFLOW,
        UTXO_ADDRESS_MISMATCH,
        UTXO_SCRIPT_MISMATCH
    }
}

private data class BitcoinSigningInput(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
    val scriptCode: ByteArray,
    val witnessScript: ByteArray,
    val address: String?,
    val derivationPath: String?,
    val scriptPubKey: String?,
    val txid: String,
    val valueSats: Long,
    val vout: Long
)

private data class BitcoinSerializedOutput(
    val script: ByteArray,
    val valueSats: Long
)

private data class Bech32Decoded(
    val hrp: String,
    val words: List<Int>
)

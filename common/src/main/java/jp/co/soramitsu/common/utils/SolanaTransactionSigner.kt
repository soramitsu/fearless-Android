package jp.co.soramitsu.common.utils

import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import org.bouncycastle.util.encoders.Base64
import java.math.BigInteger

object SolanaTransactionSigner {
    private const val SIGNATURE_BYTES = 64
    private const val PUBLIC_KEY_BYTES = 32
    private const val VERSION_PREFIX_MASK = 0x80
    private const val VERSION_VALUE_MASK = 0x7f
    private const val MAX_COMPACT_U16_BYTES = 3
    private val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()
    private val BASE64_TRANSACTION = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")

    fun parseSerializedTransaction(transaction: ByteArray): ParsedSolanaTransaction {
        requireTransaction(transaction)

        val signatureCount = readCompactU16(transaction, 0, ErrorCode.INVALID_SIGNATURE_COUNT)
        val signaturesOffset = signatureCount.offset
        val messageOffset = signaturesOffset + signatureCount.value * SIGNATURE_BYTES

        if (signatureCount.value < 1) {
            throw SolanaTransactionException(ErrorCode.MISSING_SIGNATURE_SLOT)
        }

        ensureAvailable(transaction, signaturesOffset, signatureCount.value * SIGNATURE_BYTES, ErrorCode.TRUNCATED_SIGNATURES)
        ensureAvailable(transaction, messageOffset, 1, ErrorCode.MISSING_MESSAGE)

        val messageBytes = transaction.copyOfRange(messageOffset, transaction.size)
        val message = parseMessage(messageBytes)

        if (signatureCount.value < message.requiredSignatures) {
            throw SolanaTransactionException(ErrorCode.MISSING_REQUIRED_SIGNATURE_SLOTS)
        }

        return message.copy(
            messageBytes = messageBytes,
            messageOffset = messageOffset,
            signatureCount = signatureCount.value,
            signaturesOffset = signaturesOffset
        )
    }

    fun parseSerializedTransaction(transactionBase64: String): ParsedSolanaTransaction {
        return parseSerializedTransaction(decodeBase64Transaction(transactionBase64))
    }

    fun signSerializedTransaction(
        mnemonic: String,
        transaction: ByteArray,
        expectedSigner: String? = null,
        derivationPath: String = UniversalWalletDerivationPaths.SOLANA_DEFAULT,
        passphrase: String = ""
    ): SignedSolanaTransaction {
        val parsed = parseSerializedTransaction(transaction)
        val account = SolanaKeyDerivation.deriveAccount(
            mnemonic = mnemonic,
            passphrase = passphrase,
            derivationPath = derivationPath
        )

        if (expectedSigner != null && expectedSigner != account.address) {
            throw SolanaTransactionException(ErrorCode.SIGNER_MISMATCH)
        }

        val signerIndex = parsed.accountKeys.indexOf(account.address)

        if (signerIndex < 0) {
            throw SolanaTransactionException(ErrorCode.SIGNER_NOT_FOUND)
        }
        if (signerIndex >= parsed.requiredSignatures) {
            throw SolanaTransactionException(ErrorCode.SIGNER_NOT_REQUIRED)
        }
        if (signerIndex >= parsed.signatureCount) {
            throw SolanaTransactionException(ErrorCode.MISSING_SIGNATURE_SLOT)
        }

        val signature = SolanaSigner.signMessage(account.privateKey, parsed.messageBytes)
        val signedTransaction = transaction.copyOf()
        signature.copyInto(signedTransaction, parsed.signaturesOffset + signerIndex * SIGNATURE_BYTES)

        return SignedSolanaTransaction(
            requiredSignatures = parsed.requiredSignatures,
            signedTransaction = signedTransaction,
            signedTransactionBase64 = Base64.toBase64String(signedTransaction),
            signatureBase58 = base58Encode(signature),
            signer = account.address,
            version = parsed.version
        )
    }

    fun signSerializedTransaction(
        mnemonic: String,
        transactionBase64: String,
        expectedSigner: String? = null,
        derivationPath: String = UniversalWalletDerivationPaths.SOLANA_DEFAULT,
        passphrase: String = ""
    ): SignedSolanaTransaction {
        return signSerializedTransaction(
            mnemonic = mnemonic,
            transaction = decodeBase64Transaction(transactionBase64),
            expectedSigner = expectedSigner,
            derivationPath = derivationPath,
            passphrase = passphrase
        )
    }

    private fun parseMessage(message: ByteArray): ParsedSolanaTransaction {
        var offset = 0
        var version = SolanaTransactionVersion.Legacy

        if ((message[offset].toInt() and VERSION_PREFIX_MASK) != 0) {
            val versionValue = message[offset].toInt() and VERSION_VALUE_MASK
            if (versionValue != 0) {
                throw SolanaTransactionException(ErrorCode.UNSUPPORTED_TRANSACTION_VERSION)
            }

            version = SolanaTransactionVersion.V0
            offset += 1
        }

        ensureAvailable(message, offset, 3, ErrorCode.MALFORMED_MESSAGE_HEADER)

        val requiredSignatures = message[offset].toInt() and 0xff
        val readonlySignedAccounts = message[offset + 1].toInt() and 0xff
        val readonlyUnsignedAccounts = message[offset + 2].toInt() and 0xff

        offset += 3

        val accountCount = readCompactU16(message, offset, ErrorCode.INVALID_ACCOUNT_KEYS)
        offset = accountCount.offset

        if (accountCount.value < requiredSignatures) {
            throw SolanaTransactionException(ErrorCode.INVALID_REQUIRED_SIGNATURE_COUNT)
        }
        if (readonlySignedAccounts > requiredSignatures) {
            throw SolanaTransactionException(ErrorCode.INVALID_REQUIRED_SIGNATURE_COUNT)
        }
        if (readonlyUnsignedAccounts > accountCount.value - requiredSignatures) {
            throw SolanaTransactionException(ErrorCode.INVALID_REQUIRED_SIGNATURE_COUNT)
        }

        ensureAvailable(message, offset, accountCount.value * PUBLIC_KEY_BYTES, ErrorCode.TRUNCATED_ACCOUNT_KEYS)

        val accountKeys = mutableListOf<String>()
        repeat(accountCount.value) {
            accountKeys.add(base58Encode(message.copyOfRange(offset, offset + PUBLIC_KEY_BYTES)))
            offset += PUBLIC_KEY_BYTES
        }

        ensureAvailable(message, offset, PUBLIC_KEY_BYTES, ErrorCode.TRUNCATED_RECENT_BLOCKHASH)
        val recentBlockhash = base58Encode(message.copyOfRange(offset, offset + PUBLIC_KEY_BYTES))
        offset += PUBLIC_KEY_BYTES

        val instructions = skipCompiledInstructions(message, offset)
        offset = instructions.offset

        var addressTableLookupCount = 0
        if (version == SolanaTransactionVersion.V0) {
            val addressTableLookups = skipAddressTableLookups(message, offset)
            addressTableLookupCount = addressTableLookups.count
            offset = addressTableLookups.offset
        }

        if (offset != message.size) {
            throw SolanaTransactionException(ErrorCode.TRAILING_MESSAGE_BYTES)
        }

        return ParsedSolanaTransaction(
            accountKeys = accountKeys,
            addressTableLookupCount = addressTableLookupCount,
            instructionCount = instructions.count,
            messageBytes = ByteArray(0),
            messageOffset = 0,
            readonlySignedAccounts = readonlySignedAccounts,
            readonlyUnsignedAccounts = readonlyUnsignedAccounts,
            recentBlockhash = recentBlockhash,
            requiredSignatures = requiredSignatures,
            signatureCount = 0,
            signaturesOffset = 0,
            version = version
        )
    }

    private fun skipCompiledInstructions(message: ByteArray, offset: Int): CountAndOffset {
        var nextOffset = offset
        val instructionCount = readCompactU16(message, nextOffset, ErrorCode.INVALID_INSTRUCTION_COUNT)
        nextOffset = instructionCount.offset

        repeat(instructionCount.value) {
            ensureAvailable(message, nextOffset, 1, ErrorCode.TRUNCATED_INSTRUCTION_PROGRAM)
            nextOffset += 1

            val accountIndexCount = readCompactU16(message, nextOffset, ErrorCode.INVALID_INSTRUCTION_ACCOUNTS)
            nextOffset = accountIndexCount.offset
            ensureAvailable(message, nextOffset, accountIndexCount.value, ErrorCode.TRUNCATED_INSTRUCTION_ACCOUNTS)
            nextOffset += accountIndexCount.value

            val dataLength = readCompactU16(message, nextOffset, ErrorCode.INVALID_INSTRUCTION_DATA)
            nextOffset = dataLength.offset
            ensureAvailable(message, nextOffset, dataLength.value, ErrorCode.TRUNCATED_INSTRUCTION_DATA)
            nextOffset += dataLength.value
        }

        return CountAndOffset(instructionCount.value, nextOffset)
    }

    private fun skipAddressTableLookups(message: ByteArray, offset: Int): CountAndOffset {
        var nextOffset = offset
        val lookupCount = readCompactU16(message, nextOffset, ErrorCode.INVALID_ADDRESS_TABLE_LOOKUPS)
        nextOffset = lookupCount.offset

        repeat(lookupCount.value) {
            ensureAvailable(message, nextOffset, PUBLIC_KEY_BYTES, ErrorCode.TRUNCATED_ADDRESS_TABLE_LOOKUP)
            nextOffset += PUBLIC_KEY_BYTES

            val writableCount = readCompactU16(message, nextOffset, ErrorCode.INVALID_ADDRESS_TABLE_WRITABLE_INDEXES)
            nextOffset = writableCount.offset
            ensureAvailable(message, nextOffset, writableCount.value, ErrorCode.TRUNCATED_ADDRESS_TABLE_WRITABLE_INDEXES)
            nextOffset += writableCount.value

            val readonlyCount = readCompactU16(message, nextOffset, ErrorCode.INVALID_ADDRESS_TABLE_READONLY_INDEXES)
            nextOffset = readonlyCount.offset
            ensureAvailable(message, nextOffset, readonlyCount.value, ErrorCode.TRUNCATED_ADDRESS_TABLE_READONLY_INDEXES)
            nextOffset += readonlyCount.value
        }

        return CountAndOffset(lookupCount.value, nextOffset)
    }

    private fun readCompactU16(bytes: ByteArray, offset: Int, code: ErrorCode): CountAndOffset {
        var value = 0
        var shift = 0
        var nextOffset = offset

        repeat(MAX_COMPACT_U16_BYTES) {
            ensureAvailable(bytes, nextOffset, 1, code)

            val byte = bytes[nextOffset].toInt() and 0xff
            nextOffset += 1
            value = value or ((byte and 0x7f) shl shift)

            if ((byte and 0x80) == 0) {
                return CountAndOffset(value, nextOffset)
            }

            shift += 7
        }

        throw SolanaTransactionException(code)
    }

    private fun ensureAvailable(bytes: ByteArray, offset: Int, length: Int, code: ErrorCode) {
        if (offset < 0 || length < 0 || offset + length > bytes.size) {
            throw SolanaTransactionException(code)
        }
    }

    private fun requireTransaction(transaction: ByteArray) {
        if (transaction.isEmpty()) {
            throw SolanaTransactionException(ErrorCode.EMPTY_TRANSACTION)
        }
    }

    private fun decodeBase64Transaction(transactionBase64: String): ByteArray {
        if (transactionBase64.isEmpty()) {
            throw SolanaTransactionException(ErrorCode.EMPTY_TRANSACTION)
        }
        if (!BASE64_TRANSACTION.matches(transactionBase64)) {
            throw SolanaTransactionException(ErrorCode.INVALID_TRANSACTION_BASE64)
        }

        return Base64.decode(transactionBase64)
    }

    private fun base58Encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) {
            return ""
        }

        var value = BigInteger(1, bytes)
        val output = StringBuilder()
        val radix = BigInteger.valueOf(BASE58_ALPHABET.size.toLong())

        while (value > BigInteger.ZERO) {
            val divRem = value.divideAndRemainder(radix)
            output.append(BASE58_ALPHABET[divRem[1].toInt()])
            value = divRem[0]
        }

        bytes.takeWhile { it == 0.toByte() }.forEach { _ ->
            output.append(BASE58_ALPHABET[0])
        }

        return output.reverse().toString()
    }

    data class ParsedSolanaTransaction(
        val accountKeys: List<String>,
        val addressTableLookupCount: Int,
        val instructionCount: Int,
        val messageBytes: ByteArray,
        val messageOffset: Int,
        val readonlySignedAccounts: Int,
        val readonlyUnsignedAccounts: Int,
        val recentBlockhash: String,
        val requiredSignatures: Int,
        val signatureCount: Int,
        val signaturesOffset: Int,
        val version: SolanaTransactionVersion
    )

    data class SignedSolanaTransaction(
        val requiredSignatures: Int,
        val signedTransaction: ByteArray,
        val signedTransactionBase64: String,
        val signatureBase58: String,
        val signer: String,
        val version: SolanaTransactionVersion
    )

    class SolanaTransactionException(val code: ErrorCode) : IllegalArgumentException(code.name)

    enum class SolanaTransactionVersion {
        Legacy,
        V0
    }

    enum class ErrorCode {
        EMPTY_TRANSACTION,
        INVALID_TRANSACTION_BASE64,
        INVALID_SIGNATURE_COUNT,
        MISSING_SIGNATURE_SLOT,
        TRUNCATED_SIGNATURES,
        MISSING_MESSAGE,
        UNSUPPORTED_TRANSACTION_VERSION,
        MALFORMED_MESSAGE_HEADER,
        INVALID_ACCOUNT_KEYS,
        INVALID_REQUIRED_SIGNATURE_COUNT,
        TRUNCATED_ACCOUNT_KEYS,
        TRUNCATED_RECENT_BLOCKHASH,
        INVALID_INSTRUCTION_COUNT,
        TRUNCATED_INSTRUCTION_PROGRAM,
        INVALID_INSTRUCTION_ACCOUNTS,
        TRUNCATED_INSTRUCTION_ACCOUNTS,
        INVALID_INSTRUCTION_DATA,
        TRUNCATED_INSTRUCTION_DATA,
        INVALID_ADDRESS_TABLE_LOOKUPS,
        TRUNCATED_ADDRESS_TABLE_LOOKUP,
        INVALID_ADDRESS_TABLE_WRITABLE_INDEXES,
        TRUNCATED_ADDRESS_TABLE_WRITABLE_INDEXES,
        INVALID_ADDRESS_TABLE_READONLY_INDEXES,
        TRUNCATED_ADDRESS_TABLE_READONLY_INDEXES,
        TRAILING_MESSAGE_BYTES,
        MISSING_REQUIRED_SIGNATURE_SLOTS,
        SIGNER_MISMATCH,
        SIGNER_NOT_FOUND,
        SIGNER_NOT_REQUIRED
    }

    private data class CountAndOffset(val value: Int, val offset: Int) {
        val count: Int
            get() = value
    }
}

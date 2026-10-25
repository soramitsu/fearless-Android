package jp.co.soramitsu.backup.passkey

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class PasskeyBackupEnvelopeMetadata(
    val storageKey: String,
    val walletId: String,
    val accountName: String,
    val createdAtMillis: Long,
    val schemaVersion: Int = PasskeyBackupContract.SCHEMA_VERSION
) {
    init {
        require(PasskeyBackupContract.requireStorageKey(storageKey) == storageKey) {
            "Passkey backup envelope storageKey must be canonical"
        }
        require(PasskeyBackupContract.requireWalletId(walletId) == walletId) {
            "Passkey backup envelope walletId must be canonical"
        }
        require(GoogleDrivePasskeyBackup.requireAccountName(accountName) == accountName) {
            "Passkey backup envelope accountName must be canonical"
        }
        PasskeyBackupContract.requireCreatedAtMillis(createdAtMillis)
        require(schemaVersion == PasskeyBackupContract.SCHEMA_VERSION) {
            "Unsupported passkey backup schemaVersion: $schemaVersion"
        }
    }

    internal fun canonicalAdditionalAuthenticatedData(): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(AAD_MAGIC)
            data.writeInt(schemaVersion)
            data.writeLong(createdAtMillis)
            data.writeCanonicalUtf8(storageKey)
            data.writeCanonicalUtf8(walletId)
            data.writeCanonicalUtf8(accountName)
        }
        return output.toByteArray()
    }

    private fun DataOutputStream.writeCanonicalUtf8(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        writeInt(encoded.size)
        write(encoded)
    }

    private companion object {
        val AAD_MAGIC = "FPBKAAD1".toByteArray(Charsets.US_ASCII)
    }
}

fun interface RecoverablePasskeyBackupKeyProvider {
    /**
     * Returns exactly 32 bytes of key material recoverable on every device owned by the same
     * wallet. Implementations must not substitute an Android Keystore device-local key.
     */
    suspend fun backupKey(metadata: PasskeyBackupEnvelopeMetadata): ByteArray
}

class UnavailableRecoverablePasskeyBackupKeyProvider : RecoverablePasskeyBackupKeyProvider {
    override suspend fun backupKey(metadata: PasskeyBackupEnvelopeMetadata): ByteArray {
        throw UnsupportedOperationException(
            "Passkey backup key recovery is unavailable until a reviewed cross-device key source is configured"
        )
    }
}

interface PasskeyBackupEnvelopeCryptography {
    /** Implementations must emit and consume [PasskeyBackupEnvelopeV1Format] envelopes. */
    fun encrypt(
        plaintext: ByteArray,
        metadata: PasskeyBackupEnvelopeMetadata,
        key: ByteArray
    ): ByteArray

    fun decrypt(
        envelope: ByteArray,
        metadata: PasskeyBackupEnvelopeMetadata,
        key: ByteArray
    ): ByteArray

    fun requireCanonicalEnvelope(envelope: ByteArray): ByteArray
}

object PasskeyBackupEnvelopeV1Format {
    internal val MAGIC = "FPBKAEAD".toByteArray(Charsets.US_ASCII)
    internal const val VERSION = 1
    internal const val ALGORITHM_AES_256_GCM = 1
    internal const val KEY_BYTES = 32
    internal const val NONCE_BYTES = 12
    internal const val TAG_BYTES = 16
    internal const val HEADER_BYTES = 16
    internal const val MAX_ENVELOPE_BYTES = 256 * 1024
    internal const val MAX_PLAINTEXT_BYTES = MAX_ENVELOPE_BYTES - HEADER_BYTES - NONCE_BYTES - TAG_BYTES
    internal const val MIN_ENVELOPE_BYTES = HEADER_BYTES + NONCE_BYTES + TAG_BYTES + 1

    private const val VERSION_OFFSET = 8
    private const val ALGORITHM_OFFSET = 9
    private const val NONCE_LENGTH_OFFSET = 10
    private const val TAG_LENGTH_OFFSET = 11
    private const val CIPHERTEXT_LENGTH_OFFSET = 12
    private const val UNSIGNED_BYTE_MASK = 0xff
    private const val BITS_PER_BYTE = 8
    private const val INT_BYTES = Int.SIZE_BYTES

    fun requireCanonical(envelope: ByteArray): ByteArray {
        parse(envelope)
        return envelope
    }

    internal fun parse(envelope: ByteArray): ParsedEnvelope {
        require(envelope.size in MIN_ENVELOPE_BYTES..MAX_ENVELOPE_BYTES) {
            "Passkey backup envelope has an invalid size"
        }
        require(envelope.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Passkey backup envelope magic is invalid"
        }
        require(envelope.unsignedByteAt(VERSION_OFFSET) == VERSION) {
            "Unsupported passkey backup envelope version"
        }
        require(envelope.unsignedByteAt(ALGORITHM_OFFSET) == ALGORITHM_AES_256_GCM) {
            "Unsupported passkey backup envelope algorithm"
        }
        require(envelope.unsignedByteAt(NONCE_LENGTH_OFFSET) == NONCE_BYTES) {
            "Passkey backup envelope nonce length is invalid"
        }
        require(envelope.unsignedByteAt(TAG_LENGTH_OFFSET) == TAG_BYTES) {
            "Passkey backup envelope tag length is invalid"
        }

        val ciphertextLength = envelope.readBigEndianInt(CIPHERTEXT_LENGTH_OFFSET)
        require(ciphertextLength in 1..MAX_PLAINTEXT_BYTES) {
            "Passkey backup envelope ciphertext length is invalid"
        }
        require(envelope.size == HEADER_BYTES + NONCE_BYTES + ciphertextLength + TAG_BYTES) {
            "Passkey backup envelope length is not canonical"
        }

        val nonceStart = HEADER_BYTES
        val ciphertextStart = nonceStart + NONCE_BYTES
        val tagStart = ciphertextStart + ciphertextLength
        return ParsedEnvelope(
            nonce = envelope.copyOfRange(nonceStart, ciphertextStart),
            ciphertext = envelope.copyOfRange(ciphertextStart, tagStart),
            tag = envelope.copyOfRange(tagStart, envelope.size)
        )
    }

    internal data class ParsedEnvelope(
        val nonce: ByteArray,
        val ciphertext: ByteArray,
        val tag: ByteArray
    )

    private fun ByteArray.unsignedByteAt(offset: Int): Int = this[offset].toInt() and UNSIGNED_BYTE_MASK

    private fun ByteArray.readBigEndianInt(offset: Int): Int {
        var result = 0
        repeat(INT_BYTES) { byteOffset ->
            result = result shl BITS_PER_BYTE or unsignedByteAt(offset + byteOffset)
        }
        return result
    }
}

class AesGcmPasskeyBackupEnvelopeCryptography(
    private val secureRandom: SecureRandom = SecureRandom()
) : PasskeyBackupEnvelopeCryptography {
    override fun encrypt(
        plaintext: ByteArray,
        metadata: PasskeyBackupEnvelopeMetadata,
        key: ByteArray
    ): ByteArray {
        require(plaintext.isNotEmpty()) { "Passkey backup plaintext must not be empty" }
        require(plaintext.size <= PasskeyBackupEnvelopeV1Format.MAX_PLAINTEXT_BYTES) {
            "Passkey backup plaintext is too large"
        }
        val normalizedKey = requireKey(key)
        val nonce = ByteArray(PasskeyBackupEnvelopeV1Format.NONCE_BYTES).also(secureRandom::nextBytes)

        val sealed = try {
            cipher(Cipher.ENCRYPT_MODE, normalizedKey, nonce).run {
                updateAAD(metadata.canonicalAdditionalAuthenticatedData())
                doFinal(plaintext)
            }
        } finally {
            normalizedKey.fill(0)
        }
        check(sealed.size == plaintext.size + PasskeyBackupEnvelopeV1Format.TAG_BYTES)
        val ciphertextLength = sealed.size - PasskeyBackupEnvelopeV1Format.TAG_BYTES

        return ByteArrayOutputStream(
            PasskeyBackupEnvelopeV1Format.HEADER_BYTES +
                PasskeyBackupEnvelopeV1Format.NONCE_BYTES + sealed.size
        ).also { output ->
            DataOutputStream(output).use { data ->
                data.write(PasskeyBackupEnvelopeV1Format.MAGIC)
                data.writeByte(PasskeyBackupEnvelopeV1Format.VERSION)
                data.writeByte(PasskeyBackupEnvelopeV1Format.ALGORITHM_AES_256_GCM)
                data.writeByte(PasskeyBackupEnvelopeV1Format.NONCE_BYTES)
                data.writeByte(PasskeyBackupEnvelopeV1Format.TAG_BYTES)
                data.writeInt(ciphertextLength)
                data.write(nonce)
                data.write(sealed, 0, ciphertextLength)
                data.write(sealed, ciphertextLength, PasskeyBackupEnvelopeV1Format.TAG_BYTES)
            }
        }.toByteArray().also(::requireCanonicalEnvelope)
    }

    override fun decrypt(
        envelope: ByteArray,
        metadata: PasskeyBackupEnvelopeMetadata,
        key: ByteArray
    ): ByteArray {
        val parsed = PasskeyBackupEnvelopeV1Format.parse(envelope)
        val normalizedKey = requireKey(key)
        val sealed = parsed.ciphertext + parsed.tag

        return try {
            cipher(Cipher.DECRYPT_MODE, normalizedKey, parsed.nonce).run {
                updateAAD(metadata.canonicalAdditionalAuthenticatedData())
                doFinal(sealed)
            }.also { plaintext ->
                require(
                    plaintext.isNotEmpty() &&
                        plaintext.size <= PasskeyBackupEnvelopeV1Format.MAX_PLAINTEXT_BYTES
                ) {
                    "Passkey backup decrypted payload has an invalid size"
                }
            }
        } catch (error: AEADBadTagException) {
            throw IllegalArgumentException("Passkey backup envelope authentication failed", error)
        } catch (error: java.security.GeneralSecurityException) {
            throw IllegalArgumentException("Passkey backup envelope decryption failed", error)
        } finally {
            normalizedKey.fill(0)
            sealed.fill(0)
        }
    }

    override fun requireCanonicalEnvelope(envelope: ByteArray): ByteArray {
        return PasskeyBackupEnvelopeV1Format.requireCanonical(envelope)
    }

    private fun requireKey(key: ByteArray): ByteArray {
        require(key.size == PasskeyBackupEnvelopeV1Format.KEY_BYTES) {
            "Passkey backup key must be exactly 32 bytes"
        }
        return key.copyOf()
    }

    private fun cipher(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray
    ): Cipher {
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                mode,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(PasskeyBackupEnvelopeV1Format.TAG_BYTES * BITS_PER_BYTE, nonce)
            )
        }
    }

    private companion object {
        const val BITS_PER_BYTE = 8
    }
}

internal fun PasskeyBackupEncryptedPayload.envelopeMetadata(): PasskeyBackupEnvelopeMetadata {
    return PasskeyBackupEnvelopeMetadata(
        storageKey = storageKey,
        walletId = walletId,
        accountName = accountName,
        createdAtMillis = createdAtMillis,
        schemaVersion = schemaVersion
    )
}

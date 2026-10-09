package jp.co.soramitsu.backup.passkey

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Public, authenticated context; no owner or wallet secret is sent to a service. */
data class PasskeyBackupKeyWrapperContext(
    val ownerSubject: String,
    val credentialId: String,
    val keyEpoch: Long,
    val envelopeMetadata: PasskeyBackupEnvelopeMetadata
) {
    init {
        require(
            ownerSubject.startsWith("owner:") &&
                canonicalBase64Url(ownerSubject.removePrefix("owner:"), OWNER_ID_BYTES, OWNER_ID_BYTES)
        ) {
            "Passkey backup owner subject is invalid"
        }
        require(canonicalBase64Url(credentialId, MIN_CREDENTIAL_BYTES, MAX_CREDENTIAL_BYTES)) {
            "Passkey backup credential ID is invalid"
        }
        require(keyEpoch >= 0) { "Passkey backup key epoch is invalid" }
    }

    override fun toString(): String = "PasskeyBackupKeyWrapperContext(redacted)"
}

/** Client-side encrypted DEK wrapper. The PRF output and plaintext DEK never appear here. */
class PasskeyBackupCredentialKeyWrapperRecord(
    val context: PasskeyBackupKeyWrapperContext,
    prfSalt: ByteArray,
    hkdfSalt: ByteArray,
    nonce: ByteArray,
    ciphertextAndTag: ByteArray
) {
    private val prfSaltBytes = prfSalt.copyOf().also { require(it.size == PRF_SALT_BYTES) }
    private val hkdfSaltBytes = hkdfSalt.copyOf().also { require(it.size == HKDF_SALT_BYTES) }
    private val nonceBytes = nonce.copyOf().also { require(it.size == GCM_NONCE_BYTES) }
    private val ciphertextBytes = ciphertextAndTag.copyOf().also { require(it.size == WRAPPED_KEY_BYTES) }

    val prfSalt: ByteArray get() = prfSaltBytes.copyOf()
    val hkdfSalt: ByteArray get() = hkdfSaltBytes.copyOf()
    val nonce: ByteArray get() = nonceBytes.copyOf()
    val ciphertextAndTag: ByteArray get() = ciphertextBytes.copyOf()

    override fun toString(): String = "PasskeyBackupCredentialKeyWrapperRecord(redacted)"
}

/**
 * Wraps a random 32-byte backup key using one native WebAuthn PRF result.
 * Native ceremony code owns PRF output lifetime and must never serialize it.
 */
class PasskeyBackupCredentialKeyWrapper(
    private val secureRandom: SecureRandom = SecureRandom()
) {
    fun newPrfSalt(): ByteArray = ByteArray(PRF_SALT_BYTES).also(secureRandom::nextBytes)

    /** Canonical opaque v1 record; it contains only public context and encrypted key material. */
    fun encodeRecord(record: PasskeyBackupCredentialKeyWrapperRecord): ByteArray {
        val context = contextBytes(record.context)
        val output = ByteArrayOutputStream(RECORD_FIXED_BYTES + context.size)
        DataOutputStream(output).use { data ->
            data.write(RECORD_DOMAIN)
            data.writeInt(WRAPPER_FORMAT_VERSION)
            data.writeInt(context.size)
            data.write(context)
            data.write(record.prfSalt)
            data.write(record.hkdfSalt)
            data.write(record.nonce)
            data.write(record.ciphertextAndTag)
        }
        return output.toByteArray()
    }

    /** The verified owner/credential/backup manifest supplies [expectedContext]. */
    fun decodeRecord(
        encoded: ByteArray,
        expectedContext: PasskeyBackupKeyWrapperContext
    ): PasskeyBackupCredentialKeyWrapperRecord {
        val expectedBytes = contextBytes(expectedContext)
        require(encoded.size == RECORD_FIXED_BYTES + expectedBytes.size) {
            "Passkey backup key wrapper record size is invalid"
        }
        return DataInputStream(ByteArrayInputStream(encoded)).use { data ->
            val magic = ByteArray(RECORD_DOMAIN.size).also(data::readFully)
            require(MessageDigest.isEqual(magic, RECORD_DOMAIN) && data.readInt() == WRAPPER_FORMAT_VERSION) {
                "Passkey backup key wrapper record format is invalid"
            }
            require(data.readInt() == expectedBytes.size) {
                "Passkey backup key wrapper context size is invalid"
            }
            val context = ByteArray(expectedBytes.size).also(data::readFully)
            require(MessageDigest.isEqual(context, expectedBytes)) {
                "Passkey backup key wrapper context mismatch"
            }
            val prfSalt = ByteArray(PRF_SALT_BYTES).also(data::readFully)
            val hkdfSalt = ByteArray(HKDF_SALT_BYTES).also(data::readFully)
            val nonce = ByteArray(GCM_NONCE_BYTES).also(data::readFully)
            val ciphertextAndTag = ByteArray(WRAPPED_KEY_BYTES).also(data::readFully)
            check(data.read() == -1)
            PasskeyBackupCredentialKeyWrapperRecord(expectedContext, prfSalt, hkdfSalt, nonce, ciphertextAndTag)
        }
    }

    fun wrap(
        backupKey: ByteArray,
        prfOutput: ByteArray,
        prfSalt: ByteArray,
        context: PasskeyBackupKeyWrapperContext
    ): PasskeyBackupCredentialKeyWrapperRecord {
        val hkdfSalt = ByteArray(HKDF_SALT_BYTES).also(secureRandom::nextBytes)
        val nonce = ByteArray(GCM_NONCE_BYTES).also(secureRandom::nextBytes)
        return try {
            wrapWithParameters(backupKey, prfOutput, prfSalt, hkdfSalt, nonce, context)
        } finally {
            hkdfSalt.fill(0)
            nonce.fill(0)
        }
    }

    internal fun wrapWithParameters(
        backupKey: ByteArray,
        prfOutput: ByteArray,
        prfSalt: ByteArray,
        hkdfSalt: ByteArray,
        nonce: ByteArray,
        context: PasskeyBackupKeyWrapperContext
    ): PasskeyBackupCredentialKeyWrapperRecord {
        require(backupKey.size == BACKUP_KEY_BYTES) { "Passkey backup key must be exactly 32 bytes" }
        requireInputLengths(prfOutput, prfSalt, hkdfSalt, nonce)
        val wrappingKey = deriveKey(prfOutput, prfSalt, hkdfSalt, context)
        return try {
            val ciphertext = aesGcm(Cipher.ENCRYPT_MODE, wrappingKey, nonce).run {
                updateAAD(additionalAuthenticatedData(context, prfSalt, hkdfSalt))
                doFinal(backupKey)
            }
            PasskeyBackupCredentialKeyWrapperRecord(context, prfSalt, hkdfSalt, nonce, ciphertext)
        } finally {
            wrappingKey.fill(0)
        }
    }

    fun unwrap(
        record: PasskeyBackupCredentialKeyWrapperRecord,
        prfOutput: ByteArray,
        expectedContext: PasskeyBackupKeyWrapperContext
    ): ByteArray {
        require(record.context == expectedContext) { "Passkey backup wrapper context mismatch" }
        val prfSalt = record.prfSalt
        val hkdfSalt = record.hkdfSalt
        val nonce = record.nonce
        requireInputLengths(prfOutput, prfSalt, hkdfSalt, nonce)
        val wrappingKey = deriveKey(prfOutput, prfSalt, hkdfSalt, expectedContext)
        val ciphertext = record.ciphertextAndTag
        return try {
            aesGcm(Cipher.DECRYPT_MODE, wrappingKey, nonce).run {
                updateAAD(additionalAuthenticatedData(expectedContext, prfSalt, hkdfSalt))
                doFinal(ciphertext)
            }.also { require(it.size == BACKUP_KEY_BYTES) }
        } catch (error: AEADBadTagException) {
            throw IllegalArgumentException("Passkey backup key wrapper authentication failed", error)
        } catch (error: java.security.GeneralSecurityException) {
            throw IllegalArgumentException("Passkey backup key unwrap failed", error)
        } finally {
            wrappingKey.fill(0)
            ciphertext.fill(0)
        }
    }

    private fun deriveKey(
        prfOutput: ByteArray,
        prfSalt: ByteArray,
        hkdfSalt: ByteArray,
        context: PasskeyBackupKeyWrapperContext
    ): ByteArray {
        val inputCopy = prfOutput.copyOf()
        val pseudorandomKey = hmac(hkdfSalt, inputCopy)
        return try {
            val info = HKDF_INFO_DOMAIN + contextBytes(context) + prfSalt + byteArrayOf(1)
            hmac(pseudorandomKey, info)
        } finally {
            inputCopy.fill(0)
            pseudorandomKey.fill(0)
        }
    }

    private fun additionalAuthenticatedData(
        context: PasskeyBackupKeyWrapperContext,
        prfSalt: ByteArray,
        hkdfSalt: ByteArray
    ): ByteArray = AAD_DOMAIN + contextBytes(context) + prfSalt + hkdfSalt

    private fun contextBytes(context: PasskeyBackupKeyWrapperContext): ByteArray {
        val metadata = context.envelopeMetadata
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(CONTEXT_DOMAIN)
            data.writeInt(WRAPPER_FORMAT_VERSION)
            data.writeCanonicalUtf8(PasskeyBackupContract.PASSKEY_RP_ID)
            data.writeCanonicalUtf8(context.ownerSubject)
            data.writeCanonicalUtf8(metadata.storageKey)
            data.writeCanonicalUtf8(metadata.walletId)
            data.writeCanonicalUtf8(metadata.accountName)
            data.writeLong(metadata.createdAtMillis)
            data.writeInt(metadata.schemaVersion)
            data.writeCanonicalUtf8(context.credentialId)
            data.writeLong(context.keyEpoch)
        }
        return output.toByteArray().also {
            require(it.size <= MAX_CONTEXT_BYTES) { "Passkey backup wrapper context is too large" }
        }
    }

    private fun DataOutputStream.writeCanonicalUtf8(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun hmac(key: ByteArray, message: ByteArray): ByteArray {
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(message)
        }
    }

    private fun aesGcm(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray
    ): Cipher {
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        }
    }

    private fun requireInputLengths(
        prfOutput: ByteArray,
        prfSalt: ByteArray,
        hkdfSalt: ByteArray,
        nonce: ByteArray
    ) {
        require(prfOutput.size == PRF_OUTPUT_BYTES) { "Passkey PRF output must be exactly 32 bytes" }
        require(prfSalt.size == PRF_SALT_BYTES) { "Passkey PRF salt must be exactly 32 bytes" }
        require(hkdfSalt.size == HKDF_SALT_BYTES) { "Passkey HKDF salt must be exactly 32 bytes" }
        require(nonce.size == GCM_NONCE_BYTES) { "Passkey wrapper nonce must be exactly 12 bytes" }
    }

    private companion object {
        val CONTEXT_DOMAIN = "FPBKWRAP1".toByteArray(Charsets.US_ASCII)
        val RECORD_DOMAIN = "FPBKWRP1".toByteArray(Charsets.US_ASCII)
        val HKDF_INFO_DOMAIN = "FPBK-PRF-KEK-v1".toByteArray(Charsets.US_ASCII)
        val AAD_DOMAIN = "FPBK-WRAP-AAD-v1".toByteArray(Charsets.US_ASCII)
        const val WRAPPER_FORMAT_VERSION = 1
        const val MAX_CONTEXT_BYTES = 4096
        const val RECORD_FIXED_BYTES = 8 + 4 + 4 + PRF_SALT_BYTES + HKDF_SALT_BYTES + GCM_NONCE_BYTES + WRAPPED_KEY_BYTES
    }
}

private const val BACKUP_KEY_BYTES = 32
private const val OWNER_ID_BYTES = 32
private const val MIN_CREDENTIAL_BYTES = 1
private const val MAX_CREDENTIAL_BYTES = 384
private const val PRF_OUTPUT_BYTES = 32
private const val PRF_SALT_BYTES = 32
private const val HKDF_SALT_BYTES = 32
private const val GCM_NONCE_BYTES = 12
private const val WRAPPED_KEY_BYTES = BACKUP_KEY_BYTES + 16
private const val GCM_TAG_BITS = 128

private fun canonicalBase64Url(
    value: String,
    minimum: Int,
    maximum: Int
): Boolean {
    if (value.isEmpty() || value.length > maximum * 2 || !value.matches(Regex("^[A-Za-z0-9_-]+$"))) return false
    return runCatching {
        val decoded = Base64.getUrlDecoder().decode(value)
        decoded.size in minimum..maximum &&
            Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == value
    }.getOrDefault(false)
}

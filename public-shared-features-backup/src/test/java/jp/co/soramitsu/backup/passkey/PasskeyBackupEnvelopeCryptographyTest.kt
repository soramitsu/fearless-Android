package jp.co.soramitsu.backup.passkey

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupEnvelopeCryptographyTest {
    private val cryptography = AesGcmPasskeyBackupEnvelopeCryptography()
    private val metadata = testEnvelopeMetadata()
    private val key = ByteArray(32) { (it + 1).toByte() }
    private val plaintext = "recoverable wallet backup".toByteArray()

    @Test
    fun `AES 256 GCM envelope round trips with canonical versioned header`() {
        val envelope = cryptography.encrypt(plaintext, metadata, key)

        assertTrue(envelope.copyOfRange(0, 8).contentEquals("FPBKAEAD".toByteArray()))
        assertTrue(envelope[8].toInt() == 1)
        assertTrue(envelope[9].toInt() == 1)
        assertTrue(envelope[10].toInt() == 12)
        assertTrue(envelope[11].toInt() == 16)
        assertArrayEquals(plaintext, cryptography.decrypt(envelope, metadata, key))
    }

    @Test
    fun `encryption uses a fresh random nonce`() {
        val first = cryptography.encrypt(plaintext, metadata, key)
        val second = cryptography.encrypt(plaintext, metadata, key)

        assertFalse(first.contentEquals(second))
        assertFalse(first.copyOfRange(16, 28).contentEquals(second.copyOfRange(16, 28)))
    }

    @Test
    fun `tampering ciphertext tag or nonce fails authentication`() {
        val envelope = cryptography.encrypt(plaintext, metadata, key)
        listOf(16, 28, envelope.lastIndex).forEach { index ->
            val tampered = envelope.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertTrue(runCatching { cryptography.decrypt(tampered, metadata, key) }.isFailure)
        }
    }

    @Test
    fun `wrong key and metadata swaps fail authentication`() {
        val envelope = cryptography.encrypt(plaintext, metadata, key)
        val alternateMetadata = listOf(
            metadata.copy(storageKey = "wallet-5678"),
            metadata.copy(walletId = "wallet-9999"),
            metadata.copy(accountName = "mallory@example.com"),
            metadata.copy(createdAtMillis = metadata.createdAtMillis + 1)
        )

        assertTrue(runCatching { cryptography.decrypt(envelope, metadata, ByteArray(32) { 99 }) }.isFailure)
        alternateMetadata.forEach { swapped ->
            assertTrue(runCatching { cryptography.decrypt(envelope, swapped, key) }.isFailure)
        }
    }

    @Test
    fun `truncation extension and noncanonical headers are rejected`() {
        val envelope = cryptography.encrypt(plaintext, metadata, key)
        (0 until envelope.size).forEach { length ->
            assertTrue(runCatching { cryptography.requireCanonicalEnvelope(envelope.copyOf(length)) }.isFailure)
        }
        assertTrue(runCatching { cryptography.requireCanonicalEnvelope(envelope + 0) }.isFailure)

        listOf(0, 8, 9, 10, 11, 12, 13, 14, 15).forEach { index ->
            val altered = envelope.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertTrue(runCatching { cryptography.requireCanonicalEnvelope(altered) }.isFailure)
        }
    }

    @Test
    fun `empty oversized plaintext and non 256 bit keys are rejected`() {
        assertTrue(runCatching { cryptography.encrypt(ByteArray(0), metadata, key) }.isFailure)
        assertTrue(runCatching { cryptography.encrypt(ByteArray(256 * 1024), metadata, key) }.isFailure)
        listOf(0, 16, 31, 33, 64).forEach { length ->
            assertTrue(runCatching { cryptography.encrypt(plaintext, metadata, ByteArray(length)) }.isFailure)
        }

        val maximumPlaintext = ByteArray(256 * 1024 - 44) { (it and 0xff).toByte() }
        val maximumEnvelope = cryptography.encrypt(maximumPlaintext, metadata, key)
        assertTrue(maximumEnvelope.size == 256 * 1024)
        assertArrayEquals(maximumPlaintext, cryptography.decrypt(maximumEnvelope, metadata, key))
    }

    @Test
    fun `decrypts shared cross platform AES GCM contract vector`() {
        val envelope = Base64.getUrlDecoder().decode(
            "RlBCS0FFQUQBAQwQAAAAHQABAgMEBQYHCAkKCxJCbuS78BFnbl_ULhb12v1I5M7-G-ZXHqwrFsgsJiQcEPtBrkqPDXxWvxW4BQ"
        )

        assertArrayEquals(
            "cross-platform-passkey-backup".toByteArray(),
            cryptography.decrypt(envelope, metadata, ByteArray(32) { (it + 1).toByte() })
        )
    }
}

internal fun testEnvelopeMetadata(
    storageKey: String = "wallet-1234",
    walletId: String = "wallet-001",
    accountName: String = "alice@example.com",
    createdAtMillis: Long = 1_767_225_600_000L
): PasskeyBackupEnvelopeMetadata = PasskeyBackupEnvelopeMetadata(
    storageKey = storageKey,
    walletId = walletId,
    accountName = accountName,
    createdAtMillis = createdAtMillis
)

internal fun validTestEnvelope(
    storageKey: String = "wallet-1234",
    walletId: String = "wallet-001",
    accountName: String = "alice@example.com",
    createdAtMillis: Long = 1_767_225_600_000L,
    plaintext: ByteArray = byteArrayOf(1, 2, 3)
): ByteArray = AesGcmPasskeyBackupEnvelopeCryptography().encrypt(
    plaintext = plaintext,
    metadata = testEnvelopeMetadata(storageKey, walletId, accountName, createdAtMillis),
    key = ByteArray(32) { (it + 1).toByte() }
)

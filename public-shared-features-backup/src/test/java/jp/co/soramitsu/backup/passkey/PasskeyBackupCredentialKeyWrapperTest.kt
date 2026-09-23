package jp.co.soramitsu.backup.passkey

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyBackupCredentialKeyWrapperTest {
    private val wrapper = PasskeyBackupCredentialKeyWrapper()
    private val owner = "owner:ERERERERERERERERERERERERERERERERERERERERERE"
    private val credential = "IiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiI"
    private val context = PasskeyBackupKeyWrapperContext(
        ownerSubject = owner,
        credentialId = credential,
        keyEpoch = 7,
        envelopeMetadata = testEnvelopeMetadata()
    )
    private val backupKey = ByteArray(32) { 0x77 }
    private val prfOutput = ByteArray(32) { 0x66 }
    private val prfSalt = ByteArray(32) { 0x33 }
    private val hkdfSalt = ByteArray(32) { 0x44 }
    private val nonce = ByteArray(12) { 0x55 }

    @Test
    fun `shared deterministic vector wraps and unwraps backup key`() {
        val record = fixedRecord()

        assertEquals(
            "m_xnd6ezMk5VjmGJjqjAVrBDJYQTp7NxcktIT8CmyM8uzo4ZphIMYP-2QRflGgs5",
            Base64.getUrlEncoder().withoutPadding().encodeToString(record.ciphertextAndTag)
        )
        assertArrayEquals(backupKey, wrapper.unwrap(record, prfOutput, context))
    }

    @Test
    fun `wrapper rejects a different PRF output and tampered encrypted fields`() {
        val record = fixedRecord()
        assertFailure { wrapper.unwrap(record, ByteArray(32) { 0x67 }, context) }
        listOf(
            recordWith(prfSalt = prfSalt.copyOf().also { it[0] = 0 }),
            recordWith(hkdfSalt = hkdfSalt.copyOf().also { it[0] = 0 }),
            recordWith(nonce = nonce.copyOf().also { it[0] = 0 }),
            recordWith(ciphertextAndTag = record.ciphertextAndTag.also { it[0] = (it[0].toInt() xor 1).toByte() }),
            recordWith(ciphertextAndTag = record.ciphertextAndTag.also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        ).forEach { modified -> assertFailure { wrapper.unwrap(modified, prfOutput, context) } }
    }

    @Test
    fun `wrapper rejects substitution of owner credential epoch and envelope metadata`() {
        val record = fixedRecord()
        val otherOwner = "owner:" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32) { 0x12 })
        val otherCredential = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32) { 0x23 })
        listOf(
            context.copy(ownerSubject = otherOwner),
            context.copy(credentialId = otherCredential),
            context.copy(keyEpoch = 8),
            context.copy(envelopeMetadata = context.envelopeMetadata.copy(storageKey = "wallet-5678")),
            context.copy(envelopeMetadata = context.envelopeMetadata.copy(walletId = "wallet-9999")),
            context.copy(envelopeMetadata = context.envelopeMetadata.copy(accountName = "mallory@example.com")),
            context.copy(envelopeMetadata = context.envelopeMetadata.copy(createdAtMillis = 1_767_225_600_001L))
        ).forEach { mismatched -> assertFailure { wrapper.unwrap(record, prfOutput, mismatched) } }
    }

    @Test
    fun `record takes copies and never exposes mutable encrypted fields`() {
        val sourcePrfSalt = prfSalt.copyOf()
        val sourceHkdfSalt = hkdfSalt.copyOf()
        val sourceNonce = nonce.copyOf()
        val sourceCiphertext = fixedRecord().ciphertextAndTag
        val record = recordWith(
            prfSalt = sourcePrfSalt,
            hkdfSalt = sourceHkdfSalt,
            nonce = sourceNonce,
            ciphertextAndTag = sourceCiphertext
        )
        sourcePrfSalt.fill(0)
        sourceHkdfSalt.fill(0)
        sourceNonce.fill(0)
        sourceCiphertext.fill(0)
        record.prfSalt.fill(0)
        record.hkdfSalt.fill(0)
        record.nonce.fill(0)
        record.ciphertextAndTag.fill(0)

        assertArrayEquals(backupKey, wrapper.unwrap(record, prfOutput, context))
        assertFalse(record.toString().contains(credential))
        assertFalse(context.toString().contains(owner))
    }

    @Test
    fun `random wrapping creates fresh PRF and AEAD parameters`() {
        val firstPrfSalt = wrapper.newPrfSalt()
        val secondPrfSalt = wrapper.newPrfSalt()
        assertEquals(32, firstPrfSalt.size)
        assertFalse(firstPrfSalt.contentEquals(secondPrfSalt))

        val first = wrapper.wrap(backupKey, prfOutput, firstPrfSalt, context)
        val second = wrapper.wrap(backupKey, prfOutput, firstPrfSalt, context)
        assertFalse(first.hkdfSalt.contentEquals(second.hkdfSalt))
        assertFalse(first.nonce.contentEquals(second.nonce))
        assertFalse(first.ciphertextAndTag.contentEquals(second.ciphertextAndTag))
        assertArrayEquals(backupKey, wrapper.unwrap(first, prfOutput, context))
        assertArrayEquals(backupKey, wrapper.unwrap(second, prfOutput, context))
    }

    @Test
    fun `wrapper rejects noncanonical identity and malformed key lengths`() {
        listOf("", "owner:not/base64", "owner:AA", "owner:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            .forEach { value -> assertFailure { context.copy(ownerSubject = value) } }
        listOf("", "a+", "a=", "_").forEach { value ->
            assertFailure { context.copy(credentialId = value) }
        }
        assertFailure { context.copy(keyEpoch = -1) }
        listOf(0, 16, 31, 33).forEach { size ->
            assertFailure { wrapper.wrapWithParameters(ByteArray(size), prfOutput, prfSalt, hkdfSalt, nonce, context) }
            assertFailure { wrapper.wrapWithParameters(backupKey, ByteArray(size), prfSalt, hkdfSalt, nonce, context) }
            assertFailure { wrapper.wrapWithParameters(backupKey, prfOutput, ByteArray(size), hkdfSalt, nonce, context) }
            assertFailure { wrapper.wrapWithParameters(backupKey, prfOutput, prfSalt, ByteArray(size), nonce, context) }
        }
        assertFailure { wrapper.wrapWithParameters(backupKey, prfOutput, prfSalt, hkdfSalt, ByteArray(11), context) }
        assertFailure { recordWith(ciphertextAndTag = ByteArray(47)) }
        assertTrue(context.toString().contains("redacted"))
    }

    private fun fixedRecord() = wrapper.wrapWithParameters(
        backupKey, prfOutput, prfSalt, hkdfSalt, nonce, context
    )

    private fun recordWith(
        prfSalt: ByteArray = this.prfSalt,
        hkdfSalt: ByteArray = this.hkdfSalt,
        nonce: ByteArray = this.nonce,
        ciphertextAndTag: ByteArray = fixedRecord().ciphertextAndTag
    ) = PasskeyBackupCredentialKeyWrapperRecord(context, prfSalt, hkdfSalt, nonce, ciphertextAndTag)

    private fun assertFailure(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }
}

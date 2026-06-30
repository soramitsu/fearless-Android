package jp.co.soramitsu.backup.passkey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyBackupContractTest {
    @Test
    fun `release config pins production challenge service`() {
        assertTrue(PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL == "https://backup.fearlesswallet.io")
        assertFalse(PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL.contains("localhost"))
        assertFalse(PasskeyBackupReleaseConfig.CHALLENGE_SERVICE_BASE_URL.contains("example"))
        assertFalse(PasskeyBackupReleaseConfig.PASSKEY_BACKUP_ENABLED)
    }

    @Test
    fun `registration json pins rp id and requires resident passkey`() {
        val json = PasskeyBackupContract.registrationOptionsJson(
            challenge = ByteArray(32) { it.toByte() },
            userId = ByteArray(16) { (it + 1).toByte() },
            userName = "user@example.com",
            displayName = "Fearless User"
        )

        assertTrue(json.contains("\"rp\":{\"id\":\"fearlesswallet.io\""))
        assertTrue(json.contains("\"residentKey\":\"required\""))
        assertTrue(json.contains("\"userVerification\":\"required\""))
        assertTrue(json.contains("\"attestation\":\"none\""))
        assertFalse(json.contains("+"))
        assertFalse(json.contains("/"))
    }

    @Test
    fun `assertion json pins rp id and user verification`() {
        val json = PasskeyBackupContract.assertionOptionsJson(ByteArray(32) { 7 })

        assertTrue(json.contains("\"rpId\":\"fearlesswallet.io\""))
        assertTrue(json.contains("\"userVerification\":\"required\""))
        assertFalse(json.contains("+"))
        assertFalse(json.contains("/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `registration rejects short challenge`() {
        PasskeyBackupContract.registrationOptionsJson(
            challenge = ByteArray(15),
            userId = ByteArray(16),
            userName = "user@example.com",
            displayName = "Fearless User"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `registration rejects unsupported relying party`() {
        PasskeyBackupContract.registrationOptionsJson(
            challenge = ByteArray(32),
            userId = ByteArray(16),
            userName = "user@example.com",
            displayName = "Fearless User",
            rpId = "example.com"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `storage payload rejects plaintext empty backup`() {
        PasskeyBackupEncryptedPayload(
            storageKey = "wallet-1234",
            walletId = "wallet-001",
            accountName = "alice@example.com",
            createdAtMillis = 1_767_225_600_000L,
            encryptedPayload = ByteArray(0)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `storage key rejects traversal characters`() {
        PasskeyBackupEncryptedPayload(
            storageKey = "../wallet",
            walletId = "wallet-001",
            accountName = "alice@example.com",
            createdAtMillis = 1_767_225_600_000L,
            encryptedPayload = byteArrayOf(1, 2, 3)
        )
    }

    @Test
    fun `encrypted payload accepts identity metadata`() {
        val payload = PasskeyBackupEncryptedPayload(
            storageKey = "wallet-1234",
            walletId = "wallet-001",
            accountName = "alice@example.com",
            createdAtMillis = 1_767_225_600_000L,
            encryptedPayload = byteArrayOf(1, 2, 3)
        )

        assertEquals("wallet-1234", payload.storageKey)
        assertEquals("wallet-001", payload.walletId)
        assertEquals("alice@example.com", payload.accountName)
        assertEquals(1_767_225_600_000L, payload.createdAtMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encrypted payload rejects blank wallet id`() {
        PasskeyBackupEncryptedPayload(
            storageKey = "wallet-1234",
            walletId = "   ",
            accountName = "alice@example.com",
            createdAtMillis = 1_767_225_600_000L,
            encryptedPayload = byteArrayOf(1, 2, 3)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encrypted payload rejects malformed account name`() {
        PasskeyBackupEncryptedPayload(
            storageKey = "wallet-1234",
            walletId = "wallet-001",
            accountName = "alice example.com",
            createdAtMillis = 1_767_225_600_000L,
            encryptedPayload = byteArrayOf(1, 2, 3)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encrypted payload rejects zero creation timestamp`() {
        PasskeyBackupEncryptedPayload(
            storageKey = "wallet-1234",
            walletId = "wallet-001",
            accountName = "alice@example.com",
            createdAtMillis = 0,
            encryptedPayload = byteArrayOf(1, 2, 3)
        )
    }
}

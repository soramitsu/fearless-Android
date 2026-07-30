package jp.co.soramitsu.common.data.storage.encrypt

import java.security.InvalidKeyException
import java.security.ProviderException
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PreferenceAesKeyPolicyTest {

    @Test
    fun `missing wrapping key is permanent when an existing wallet key must be unwrapped`() {
        assertPermanentKeyLoss(
            hasPrivateKey = false,
            hasPublicKey = false
        )
    }

    @Test
    fun `partial wrapping key is permanent when an existing wallet key must be unwrapped`() {
        assertPermanentKeyLoss(
            hasPrivateKey = true,
            hasPublicKey = false
        )
        assertPermanentKeyLoss(
            hasPrivateKey = false,
            hasPublicKey = true
        )
    }

    @Test
    fun `complete existing wrapping key is accepted`() {
        PreferenceAesKeyPolicy.requireExistingWrappingKey(
            allowCreate = false,
            hasPrivateKey = true,
            hasPublicKey = true
        )
    }

    @Test
    fun `fresh installation may create a missing wrapping key`() {
        PreferenceAesKeyPolicy.requireExistingWrappingKey(
            allowCreate = true,
            hasPrivateKey = false,
            hasPublicKey = false
        )
    }

    @Test
    fun `new wallet key is created only for an empty fresh installation`() {
        assertTrue(
            PreferenceAesKeyPolicy.mayCreateNewKey(
                wrappedKey = null,
                preferenceKeys = emptySet(),
                hasExistingWalletRecords = false
            )
        )
        assertTrue(
            PreferenceAesKeyPolicy.mayCreateNewKey(
                wrappedKey = "",
                preferenceKeys = setOf("unrelated_preference"),
                hasExistingWalletRecords = false
            )
        )
    }

    @Test
    fun `existing wrapped key wallet records or protected payload forbid key replacement`() {
        assertFalse(
            PreferenceAesKeyPolicy.mayCreateNewKey(
                wrappedKey = "wrapped-key",
                preferenceKeys = emptySet(),
                hasExistingWalletRecords = false
            )
        )
        assertFalse(
            PreferenceAesKeyPolicy.mayCreateNewKey(
                wrappedKey = null,
                preferenceKeys = emptySet(),
                hasExistingWalletRecords = true
            )
        )
        listOf(
            WalletMasterKeyAttestation.SENTINEL_KEY,
            WalletMasterKeyAttestation.PIN_CODE_KEY,
            "1:ACCESS_SECRETS",
            "1:polkadot:ACCESS_SECRETS",
            "1:SUBSTRATE_SECRETS",
            "1:ETHEREUM_SECRETS",
            "1:TON_SECRETS",
            "TON_CONNECT_session"
        ).forEach { protectedKey ->
            assertFalse(
                "Protected payload $protectedKey must forbid replacement",
                PreferenceAesKeyPolicy.mayCreateNewKey(
                    wrappedKey = null,
                    preferenceKeys = setOf(protectedKey),
                    hasExistingWalletRecords = false
                )
            )
        }
    }

    @Test
    fun `existing AES key accepts only supported lengths`() {
        listOf(16, 24, 32).forEach { size ->
            val key = ByteArray(size) { it.toByte() }
            assertArrayEquals(
                key,
                PreferenceAesKeyPolicy.requireValidExistingKey(key)
            )
        }
    }

    @Test
    fun `missing or malformed existing AES key is permanent key loss`() {
        assertPermanentAesKeyLoss(null)
        listOf(0, 1, 15, 17, 23, 25, 31, 33, 64).forEach { size ->
            assertPermanentAesKeyLoss(ByteArray(size))
        }
    }

    @Test
    fun `deterministic unwrap failures are permanent key loss`() {
        listOf(
            BadPaddingException("wrong wrapping key"),
            IllegalBlockSizeException("malformed wrapped payload"),
            InvalidKeyException("invalidated wrapping key")
        ).forEach { failure ->
            assertEquals(
                WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS,
                PreferenceAesKeyPolicy.classifyExistingWrappedKeyFailure(
                    failure
                )
            )
        }
    }

    @Test
    fun `wrong wrapped-key preference type is permanent key loss`() {
        val failure = try {
            PreferenceAesKeyPolicy.readStoredWrappedKey {
                throw ClassCastException("stored integer")
            }
            fail("Expected the wrong preference type to fail")
            error("unreachable")
        } catch (failure: WalletSecureStorageUnavailableException) {
            failure
        }

        assertEquals(
            WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS,
            failure.kind
        )
    }

    @Test
    fun `malformed or empty wrapped-key encoding is permanent key loss`() {
        listOf<(String) -> ByteArray>(
            { throw IllegalArgumentException("invalid Base64") },
            { ByteArray(0) }
        ).forEach { decoder ->
            val failure = try {
                PreferenceAesKeyPolicy.decodeStoredWrappedKey(
                    encoded = "malformed",
                    decode = decoder
                )
                fail("Expected malformed wrapped key data to fail")
                error("unreachable")
            } catch (failure: WalletSecureStorageUnavailableException) {
                failure
            }

            assertEquals(
                WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS,
                failure.kind
            )
        }
    }

    @Test
    fun `wrapped deterministic failure is found through cause chain`() {
        assertEquals(
            WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS,
            PreferenceAesKeyPolicy.classifyExistingWrappedKeyFailure(
                IllegalStateException(
                    "provider wrapper",
                    BadPaddingException("wrong wrapping key")
                )
            )
        )
    }

    @Test
    fun `provider outage and malformed cause graph remain retryable`() {
        assertEquals(
            WalletSecureStorageFailureKind.RETRYABLE,
            PreferenceAesKeyPolicy.classifyExistingWrappedKeyFailure(
                ProviderException("temporarily unavailable")
            )
        )
        val cyclic = CyclicFailure()
        assertEquals(
            WalletSecureStorageFailureKind.RETRYABLE,
            PreferenceAesKeyPolicy.classifyExistingWrappedKeyFailure(cyclic)
        )
    }

    private fun assertPermanentKeyLoss(
        hasPrivateKey: Boolean,
        hasPublicKey: Boolean
    ) {
        val failure = try {
            PreferenceAesKeyPolicy.requireExistingWrappingKey(
                allowCreate = false,
                hasPrivateKey = hasPrivateKey,
                hasPublicKey = hasPublicKey
            )
            fail("Expected a permanent key-loss failure")
            error("unreachable")
        } catch (failure: WalletSecureStorageUnavailableException) {
            failure
        }

        assertEquals(
            WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS,
            failure.kind
        )
    }

    private fun assertPermanentAesKeyLoss(key: ByteArray?) {
        val failure = try {
            PreferenceAesKeyPolicy.requireValidExistingKey(key)
            fail("Expected malformed AES key material to fail permanently")
            error("unreachable")
        } catch (failure: WalletSecureStorageUnavailableException) {
            failure
        }

        assertEquals(
            WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS,
            failure.kind
        )
    }

    private class CyclicFailure : RuntimeException() {
        override val cause: Throwable
            get() = this
    }
}

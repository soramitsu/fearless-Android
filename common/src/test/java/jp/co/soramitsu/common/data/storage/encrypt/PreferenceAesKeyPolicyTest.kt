package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.common.data.secrets.v1.SourceInternal
import jp.co.soramitsu.common.data.secrets.v1.SourceType
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceAesKeyPolicyTest {

    @Test
    fun `new key is allowed only when no wrapped key or encrypted payload exists`() {
        assertTrue(
            PreferenceAesKeyPolicy.mayCreateNewKey(
                wrappedKey = null,
                preferenceKeys = setOf("selected_language", "feature_toggle"),
                hasExistingWalletRecords = false
            )
        )

        ENCRYPTED_PREFERENCE_KEYS.forEach { encryptedPreferenceKey ->
            assertFalse(
                encryptedPreferenceKey,
                PreferenceAesKeyPolicy.mayCreateNewKey(
                    wrappedKey = null,
                    preferenceKeys = setOf("selected_language", encryptedPreferenceKey),
                    hasExistingWalletRecords = false
                )
            )
        }

        assertFalse(
            PreferenceAesKeyPolicy.mayCreateNewKey(
                wrappedKey = "existing-wrapped-key",
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
    }

    @Test
    fun `existing wrapped key can never fall back to regeneration`() {
        listOf(
            null,
            ByteArray(0),
            ByteArray(15),
            ByteArray(17),
            ByteArray(31),
            ByteArray(33)
        ).forEach { invalidKey ->
            assertThrows(WalletSecureStorageUnavailableException::class.java) {
                PreferenceAesKeyPolicy.requireValidExistingKey(invalidKey)
            }
        }
    }

    @Test
    fun `all valid AES key lengths are accepted without mutation`() {
        listOf(16, 24, 32).forEach { length ->
            val key = ByteArray(length) { index -> (index * 7).toByte() }

            assertArrayEquals(
                key,
                PreferenceAesKeyPolicy.requireValidExistingKey(key)
            )
        }
    }

    @Test
    fun `real prefixed legacy SCALE secret authenticates while malformed prefixes fail`() {
        val source = SourceInternal {
            it[Type] = SourceType.SEED.name
            it[PrivateKey] = ByteArray(32) { index -> (index + 1).toByte() }
            it[PublicKey] = ByteArray(32) { index -> (index + 33).toByte() }
            it[Nonce] = null
            it[Seed] = ByteArray(32) { index -> (index + 65).toByte() }
            it[Mnemonic] = null
            it[DerivationPath] = null
        }
        val encoded = SourceInternal.toHexString(source)

        assertTrue(encoded.startsWith("0x"))
        assertTrue(
            WalletMasterKeyAttestation.isSemanticallyValidLegacyCandidate(
                key = "security_source_legacy-address",
                plaintext = encoded
            )
        )
        assertFalse(
            WalletMasterKeyAttestation.isSemanticallyValidLegacyCandidate(
                key = "security_source_legacy-address",
                plaintext = "0x$encoded"
            )
        )
        assertFalse(
            WalletMasterKeyAttestation.isSemanticallyValidLegacyCandidate(
                key = "security_source_legacy-address",
                plaintext = encoded.replaceFirst("0x", "0X")
            )
        )
    }

    @Test
    fun `pre TON v69 access secret authenticates with its historical schema`() {
        val currentSecrets = MetaAccountSecrets(
            substrateKeyPair = BaseKeypair(
                privateKey = ByteArray(32) { index -> (index + 1).toByte() },
                publicKey = ByteArray(32) { index -> (index + 33).toByte() }
            ),
            seed = ByteArray(32) { index -> (index + 65).toByte() }
        )
        val currentBytes = MetaAccountSecrets.toByteArray(currentSecrets)
        assertTrue(currentBytes.last() == 0.toByte())
        val historicalV69 = currentBytes
            .copyOf(currentBytes.size - 1)
            .toHexString(withPrefix = true)

        assertTrue(
            WalletMasterKeyAttestation.isSemanticallyValidLegacyCandidate(
                key = "7:ACCESS_SECRETS",
                plaintext = historicalV69
            )
        )
    }

    @Test
    fun `released v04 private preference is a valid legacy authentication proof`() {
        val signingData = KeyPairSchema {
            it[PrivateKey] = ByteArray(32) { index -> (index + 1).toByte() }
            it[PublicKey] = ByteArray(32) { index -> (index + 33).toByte() }
            it[Nonce] = ByteArray(64) { index -> (index + 65).toByte() }
        }
        val encoded = KeyPairSchema.toHexString(signingData)

        assertTrue(encoded.startsWith("0x"))
        assertTrue(
            WalletMasterKeyAttestation.isSemanticallyValidLegacyCandidate(
                key = "private_1ReleasedLegacyAddress",
                plaintext = encoded
            )
        )
        assertFalse(
            WalletMasterKeyAttestation.isSemanticallyValidLegacyCandidate(
                key = "seed_1ReleasedLegacyAddress",
                plaintext = "11".repeat(32)
            )
        )
    }

    @Test
    fun `legacy v1 quarantine authenticates only its bound public key`() {
        val publicKey = ByteArray(32) { index -> (index + 33).toByte() }
        val source = SourceInternal {
            it[Type] = SourceType.SEED.name
            it[PrivateKey] = ByteArray(32) { index -> (index + 1).toByte() }
            it[PublicKey] = publicKey
            it[Nonce] = null
            it[Seed] = ByteArray(32) { index -> (index + 65).toByte() }
            it[Mnemonic] = null
            it[DerivationPath] = null
        }
        val encoded = SourceInternal.toHexString(source)
        val quarantineKey =
            WalletSecretQuarantine.legacyV1KeyForPublicKey(publicKey)

        assertTrue(
            WalletMasterKeyAttestation.isSemanticallyValidLegacyCandidate(
                key = WalletMasterKeyAttestation.semanticValidationKey(
                    quarantineKey
                )!!,
                plaintext = encoded
            )
        )
        assertFalse(
            WalletMasterKeyAttestation.isSemanticallyValidLegacyCandidate(
                key = WalletMasterKeyAttestation.semanticValidationKey(
                    WalletSecretQuarantine.legacyV1KeyForPublicKey(
                        publicKey.copyOf().apply { this[0]++ }
                    )
                )!!,
                plaintext = encoded
            )
        )
    }

    @Test
    fun `keystore loading retries once and can recover on the same caller`() {
        var attempts = 0

        val loaded = loadWalletKeyMaterialWithRetry {
            attempts++
            if (attempts == 1) error("transient provider failure")
            "loaded"
        }

        assertEquals("loaded", loaded)
        assertEquals(2, attempts)
    }

    @Test
    fun `a later call can recover after both bounded keystore attempts fail`() {
        var attempts = 0
        val loader = {
            attempts++
            if (attempts <= 2) error("provider unavailable")
            "loaded"
        }

        assertThrows(IllegalStateException::class.java) {
            loadWalletKeyMaterialWithRetry(loader)
        }
        assertEquals("loaded", loadWalletKeyMaterialWithRetry(loader))
        assertEquals(3, attempts)
    }

    @Test
    fun `only exactly six ASCII digits are a valid released PIN`() {
        assertTrue(
            WalletMasterKeyAttestation.isSemanticallyValidLegacyCandidate(
                WalletMasterKeyAttestation.PIN_CODE_KEY,
                "012345"
            )
        )
        listOf(
            "",
            "12345",
            "1234567",
            "12a456",
            "١٢٣٤٥٦",
            "１２３４５６",
            "12345\u0000"
        ).forEach { invalid ->
            assertFalse(
                WalletMasterKeyAttestation.isSemanticallyValidLegacyCandidate(
                    WalletMasterKeyAttestation.PIN_CODE_KEY,
                    invalid
                )
            )
        }
    }

    @Test
    fun `TON Connect mutation journal remains protected key material`() {
        assertTrue(
            WalletMasterKeyAttestation.isProtectedPayloadKey(
                TonConnectStorageKeys.MUTATION_JOURNAL_KEY
            )
        )
        assertEquals(
            TonConnectStorageKeys.MUTATION_JOURNAL_KEY,
            WalletMasterKeyAttestation.semanticValidationKey(
                TonConnectStorageKeys.MUTATION_JOURNAL_KEY
            )
        )
    }

    private companion object {
        val ENCRYPTED_PREFERENCE_KEYS = listOf(
            "pin_code",
            "security_source_1FakeLegacyAddress",
            "7:ACCESS_SECRETS",
            "7:deadbeef:ACCESS_SECRETS",
            "7:SUBSTRATE_SECRETS",
            "7:ETHEREUM_SECRETS",
            "7:TON_SECRETS",
            "TON_CONNECT_client-id",
            TonConnectStorageKeys.MUTATION_JOURNAL_KEY,
            "private_1LegacyAddress",
            "seed_1LegacyAddress",
            "entropy_1LegacyAddress",
            "derivation_1LegacyAddress",
            "wallet_secret_mutation_journal_v1",
            "wallet_secret_mutation_stage:operation-id",
            "wallet_secret_mutation_backup:operation-id",
            "wallet_secret_quarantine:7:ACCESS_SECRETS",
            WalletMasterKeyAttestation.SENTINEL_KEY
        )
    }
}

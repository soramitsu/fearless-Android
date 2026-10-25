package jp.co.soramitsu.coredb.migrations

import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.coredb.migrations.ShippedWalletFixtures.ETH_SEED_HEX
import jp.co.soramitsu.coredb.migrations.shipped.ShippedSecretWriters
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySecretConversionPlanTest {

    @Test
    fun convertedLegacySecretIsArchivedByteForByteNeverRemoved() {
        val wallet = ShippedWalletFixtures.rawSeedDeclinedEthereum()
        val identity = ShippedWalletFixtures.identity(META_ID, wallet)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(ACCESS, wallet.accessSecretsHex)
        }

        convert(preferences, identity).commit(preferences)

        assertFalse(preferences.hasKey(ACCESS))
        assertEquals(
            wallet.accessSecretsHex,
            preferences.getDecryptedString(WalletSecretArchive.keyFor(ACCESS))
        )
        val substrate = SubstrateSecrets.read(checkNotNull(preferences.getDecryptedString(SUBSTRATE)))
        assertArrayEquals(
            wallet.substrateKeypair.privateKey,
            substrate[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PrivateKey]
        )
        assertFalse(preferences.hasKey(ETHEREUM))
        assertNoQuarantine(preferences)
    }

    @Test
    fun ethereumFailureScopesTheLegacyPayloadToEthereum() {
        val wallet = ShippedWalletFixtures.rawSeedDeclinedEthereum()
        val expected = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 3 })
        val other = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 4 })
        val legacy = ShippedSecretWriters.MetaAccountSecrets(
            substrateKeyPair = wallet.substrateKeypair,
            seed = null,
            ethereumKeypair = jp.co.soramitsu.common.data.Keypair(
                publicKey = expected.publicKey,
                privateKey = other.privateKey
            )
        ).toHexString()
        val identity = ShippedWalletFixtures.identity(
            metaId = META_ID,
            wallet = wallet,
            ethereumPublicKey = expected.publicKey,
            ethereumAddress = expected.publicKey.ethereumAddressFromPublicKey()
        )
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(ACCESS, legacy)
        }

        convert(preferences, identity).commit(preferences)

        assertTrue(preferences.hasKey(SUBSTRATE))
        assertFalse(preferences.hasKey(ETHEREUM))
        assertFalse(preferences.hasKey(ACCESS))
        assertEquals(
            legacy,
            preferences.getDecryptedString(WalletSecretQuarantine.keyFor(ETHEREUM))
        )
        assertFalse(preferences.hasKey(WalletSecretQuarantine.keyFor(ACCESS)))
    }

    @Test
    fun substrateFailureQuarantinesTheLegacyPayloadAndKeepsEthereum() {
        val ethereum = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 5 })
        val substrate = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 6 })
        val other = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 7 })
        val legacy = ShippedSecretWriters.MetaAccountSecrets(
            substrateKeyPair = jp.co.soramitsu.common.data.Keypair(
                publicKey = substrate.publicKey,
                privateKey = other.privateKey
            ),
            ethereumKeypair = ethereum
        ).toHexString()
        val identity = WalletPublicIdentity(
            metaId = META_ID,
            substratePublicKey = substrate.publicKey,
            substrateCryptoType = jp.co.soramitsu.core.models.CryptoType.ECDSA,
            substrateCryptoTypeWasPresent = true,
            substrateAccountId = substrate.publicKey.substrateAccountId(),
            ethereumPublicKey = ethereum.publicKey,
            ethereumAddress = ethereum.publicKey.ethereumAddressFromPublicKey(),
            tonPublicKey = null
        )
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(ACCESS, legacy)
        }

        convert(preferences, identity).commit(preferences)

        assertFalse(preferences.hasKey(SUBSTRATE))
        assertTrue(preferences.hasKey(ETHEREUM))
        assertEquals(
            legacy,
            preferences.getDecryptedString(WalletSecretQuarantine.keyFor(ACCESS))
        )
    }

    @Test
    fun activeEthereumKeyFrom4xWinsAndTheLegacyCopyIsArchived() {
        // 3.7.4 import with an Ethereum seed, then the same Ethereum key in
        // its own V3 entry with a different encoding (4.2.0 kept the path).
        val wallet = ShippedWalletFixtures.rawSeedWithEthereum()
        val added = ShippedSecretWriters.importAdditionalAccount420(ETH_SEED_HEX)
        val identity = ShippedWalletFixtures.identity(META_ID, wallet)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(ACCESS, wallet.accessSecretsHex)
            putEncryptedString(ETHEREUM, added.ethereumSecretsHex)
        }

        convert(preferences, identity).commit(preferences)

        assertEquals(added.ethereumSecretsHex, preferences.getDecryptedString(ETHEREUM))
        assertTrue(preferences.hasKey(SUBSTRATE))
        assertEquals(
            wallet.accessSecretsHex,
            preferences.getDecryptedString(WalletSecretArchive.keyFor(ACCESS))
        )
        assertNoQuarantine(preferences)
    }

    @Test
    fun conflictingTargetThatProvesNothingIsNeitherOverwrittenNorAReasonToFail() {
        // A 4.2.0 Keystore failure stored an empty or foreign V3 entry next to
        // a legacy payload that still proves the wallet. Failing here would
        // stop the upgrade for every wallet on the device.
        val wallet = ShippedWalletFixtures.rawSeedDeclinedEthereum()
        val identity = ShippedWalletFixtures.identity(META_ID, wallet)
        listOf("conflicting-material", "").forEach { stored ->
            val preferences = HashMapEncryptedPreferences().apply {
                putEncryptedString(ACCESS, wallet.accessSecretsHex)
                putEncryptedString(SUBSTRATE, stored)
            }

            convert(preferences, identity).commit(preferences)

            assertEquals(stored, preferences.getDecryptedString(SUBSTRATE))
            assertFalse(preferences.hasKey(ACCESS))
            assertEquals(
                wallet.accessSecretsHex,
                preferences.getDecryptedString(WalletSecretQuarantine.keyFor(ACCESS))
            )
        }
    }

    @Test
    fun anArchiveAlreadyHoldingTheExactPayloadCompletesTheMove() {
        val wallet = ShippedWalletFixtures.rawSeedDeclinedEthereum()
        val identity = ShippedWalletFixtures.identity(META_ID, wallet)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(ACCESS, wallet.accessSecretsHex)
            putEncryptedString(WalletSecretArchive.keyFor(ACCESS), wallet.accessSecretsHex)
        }

        convert(preferences, identity).commit(preferences)

        assertFalse(preferences.hasKey(ACCESS))
        assertEquals(
            wallet.accessSecretsHex,
            preferences.getDecryptedString(WalletSecretArchive.keyFor(ACCESS))
        )
        assertFalse(preferences.hasKey(WalletSecretArchive.keyFor(ACCESS, slot = 1)))
        assertTrue(preferences.hasKey(SUBSTRATE))
    }

    @Test
    fun anOccupiedArchiveSlotIsNeverOverwritten() {
        val wallet = ShippedWalletFixtures.rawSeedDeclinedEthereum()
        val identity = ShippedWalletFixtures.identity(META_ID, wallet)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(ACCESS, wallet.accessSecretsHex)
            putEncryptedString(WalletSecretArchive.keyFor(ACCESS), "an earlier archive")
        }

        convert(preferences, identity).commit(preferences)

        assertEquals(
            "an earlier archive",
            preferences.getDecryptedString(WalletSecretArchive.keyFor(ACCESS))
        )
        assertEquals(
            wallet.accessSecretsHex,
            preferences.getDecryptedString(WalletSecretArchive.keyFor(ACCESS, slot = 1))
        )
    }

    @Test
    fun archiveNamesRoundTripAndRejectNestedNames() {
        assertEquals("wallet_secret_archive:v1:7:ACCESS_SECRETS", WalletSecretArchive.keyFor("7:ACCESS_SECRETS"))
        assertEquals(
            "7:ACCESS_SECRETS",
            WalletSecretArchive.sourceKeyOf(WalletSecretArchive.keyFor("7:ACCESS_SECRETS", slot = 12))
        )
        assertEquals("7:TON_SECRETS", WalletSecretArchive.sourceKeyOf("wallet_secret_archive:v1:7:TON_SECRETS"))
        assertNull(WalletSecretArchive.sourceKeyOf("wallet_secret_quarantine:7:TON_SECRETS"))
        assertThrows(IllegalArgumentException::class.java) {
            WalletSecretArchive.keyFor(WalletSecretQuarantine.keyFor("7:TON_SECRETS"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WalletSecretArchive.keyFor("7:TON_SECRETS", slot = WalletSecretArchive.MAX_SLOTS)
        }
        // The archive prefix starts with a letter, so the orphan inventory's
        // numeric active namespaces can never include an archive key.
        assertNull(WalletActiveSecretKeyParser.parse(WalletSecretArchive.keyFor("7:TON_SECRETS")))
    }

    @Test
    fun convertedEthereumPayloadKeepsTheShippedPrivateKey() {
        val wallet = ShippedWalletFixtures.rawSeedWithEthereum()
        val identity = ShippedWalletFixtures.identity(META_ID, wallet)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(ACCESS, wallet.accessSecretsHex)
        }

        convert(preferences, identity).commit(preferences)

        val ethereum = EthereumSecrets.read(checkNotNull(preferences.getDecryptedString(ETHEREUM)))
        assertArrayEquals(
            checkNotNull(wallet.ethereumKeypair).privateKey,
            ethereum[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey]
        )
    }

    private fun convert(
        preferences: HashMapEncryptedPreferences,
        identity: WalletPublicIdentity,
        snapshot: EncryptedPreferenceSnapshot =
            checkNotNull(preferences.getDecryptedStringSnapshot(ACCESS))
    ): LegacySecretConversionPlan {
        val plan = preferences.planLegacySecretConversion(
            identity = identity,
            legacySourceKey = ACCESS,
            legacySnapshot = snapshot,
            replacement = WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                encoded = snapshot.plaintext,
                identity = identity
            ),
            walletRootSecretValidation = WalletRootSecretValidator
        )
        plan.requireReady(preferences)
        return plan
    }

    private fun assertNoQuarantine(preferences: HashMapEncryptedPreferences) {
        assertTrue(
            preferences.keysWithPrefixes(
                prefixes = setOf(WalletSecretQuarantine.KEY_PREFIX, "wallet_public_identity_recovery:"),
                maxResultCount = 16,
                maxKeyBytes = 256,
                maxTotalKeyBytes = 4_096
            ).isEmpty()
        )
    }

    private companion object {
        const val META_ID = 5L
        const val ACCESS = "5:ACCESS_SECRETS"
        const val SUBSTRATE = "5:SUBSTRATE_SECRETS"
        const val ETHEREUM = "5:ETHEREUM_SECRETS"
    }
}

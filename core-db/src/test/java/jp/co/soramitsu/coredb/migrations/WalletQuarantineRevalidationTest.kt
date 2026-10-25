package jp.co.soramitsu.coredb.migrations

import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.migrations.ShippedWalletFixtures.OTHER_SEED_HEX
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Internal builds 230 and 231 quarantined secrets this build accepts and
 * flagged the secrets of wallets deleted on 4.x. Every case here starts from
 * that stored state and proves the start-up pass frees it without deleting
 * or rewriting any key (addendum U0.3, AND-S1, AND-S4).
 */
class WalletQuarantineRevalidationTest {

    private val wallet = ShippedWalletFixtures.rawSeedDeclinedEthereum()
    private val identity = ShippedWalletFixtures.identity(META_ID, wallet)

    @Test
    fun orphanQuarantinesAndMarkersOfDeletedWalletsAreArchivedAndCleared() {
        val deletedKeys = listOf("9:SUBSTRATE_SECRETS", "9:ETHEREUM_SECRETS", "9:TON_SECRETS")
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(SUBSTRATE, convertedSubstrate())
            deletedKeys.forEach { activeKey ->
                putEncryptedString(WalletSecretQuarantine.keyFor(activeKey), "deleted-$activeKey")
                putEncryptedString(
                    WalletPublicIdentityRecovery.keyFor(9L, activeKey),
                    WalletPublicIdentityRecovery.MARKER_VALUE
                )
            }
            // A marker whose quarantine is already gone.
            putEncryptedString(
                WalletPublicIdentityRecovery.keyFor(10L, "10:TON_SECRETS"),
                WalletPublicIdentityRecovery.MARKER_VALUE
            )
        }

        val report = revalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))

        deletedKeys.forEach { activeKey ->
            assertEquals(
                "deleted-$activeKey",
                preferences.getDecryptedString(WalletSecretArchive.keyFor(activeKey))
            )
        }
        assertEquals(3, report.archived)
        assertEquals(1, report.markersCleared)
        assertFalse(WalletSecretAccessGuard(preferences).hasAnyRecoveryState())
        assertEquals(convertedSubstrate(), preferences.getDecryptedString(SUBSTRATE))
    }

    @Test
    fun quarantinedRawSeedLegacySecretConvertsAndIsArchived() {
        // What 230/231 left for AND-V2: the whole legacy payload quarantined.
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(WalletSecretQuarantine.keyFor(ACCESS), wallet.accessSecretsHex)
        }

        val report = revalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))

        assertEquals(1, report.released)
        val substrate = SubstrateSecrets.read(checkNotNull(preferences.getDecryptedString(SUBSTRATE)))
        assertArrayEquals(
            wallet.substrateKeypair.privateKey,
            substrate[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PrivateKey]
        )
        assertEquals(
            wallet.accessSecretsHex,
            preferences.getDecryptedString(WalletSecretArchive.keyFor(ACCESS))
        )
        assertFalse(WalletSecretAccessGuard(preferences).hasAnyRecoveryState())
    }

    @Test
    fun quarantinedV3KeyThatProvesItsIdentityMovesBackByteForByte() {
        // A drifted seed: the strict pre-K1 validator quarantined this, the
        // keypair still proves the identity.
        val drifted = SubstrateSecrets(
            substrateKeyPair = wallet.substrateKeypair,
            seed = OTHER_SEED_HEX.removePrefix("0x").fromHex(),
            substrateDerivationPath = "//drifted"
        ).toHexString()
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(WalletSecretQuarantine.keyFor(SUBSTRATE), drifted)
        }

        val report = revalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))

        assertEquals(1, report.released)
        assertEquals(drifted, preferences.getDecryptedString(SUBSTRATE))
        assertFalse(preferences.hasKey(WalletSecretQuarantine.keyFor(SUBSTRATE)))
    }

    @Test
    fun keyThatStillFailsSigningIntegrityStaysExactlyWhereItIs() {
        val forged = SubstrateSecrets(
            substrateKeyPair = jp.co.soramitsu.common.data.Keypair(
                publicKey = wallet.substratePublicKey,
                privateKey = otherKeypair().privateKey
            )
        ).toHexString()
        val states = listOf(
            forged,
            // The decryptor reports an unreadable value as empty.
            ""
        )
        states.forEach { stored ->
            val preferences = HashMapEncryptedPreferences().apply {
                putEncryptedString(WalletSecretQuarantine.keyFor(SUBSTRATE), stored)
            }

            val report = revalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))

            assertEquals(0, report.released)
            assertEquals(stored, preferences.getDecryptedString(WalletSecretQuarantine.keyFor(SUBSTRATE)))
            assertFalse(preferences.hasKey(SUBSTRATE))
        }
    }

    @Test
    fun aMarkerTheCurrentChecksNoLongerApplyIsClearedAndTheKeyMovesBack() {
        // 230/231 left a marker and a quarantine for a wallet whose identity
        // and key pass this build's checks: both are released on this start.
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(WalletSecretQuarantine.keyFor(SUBSTRATE), convertedSubstrate())
            putEncryptedString(
                WalletPublicIdentityRecovery.keyFor(META_ID, SUBSTRATE),
                WalletPublicIdentityRecovery.MARKER_VALUE
            )
        }

        val report = revalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))

        assertEquals(1, report.markersCleared)
        assertEquals(1, report.released)
        assertEquals(convertedSubstrate(), preferences.getDecryptedString(SUBSTRATE))
        assertFalse(preferences.hasKey(WalletSecretQuarantine.keyFor(SUBSTRATE)))
        assertFalse(WalletSecretAccessGuard(preferences).hasAnyRecoveryState())
    }

    @Test
    fun aMarkerOnAnIdentityThatIsStillMalformedStaysAndScopesOnlyItsKey() {
        // The recorded account id does not belong to the recorded public key.
        val malformed = identity.copy(substrateAccountId = ByteArray(32) { 9 })
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(SUBSTRATE, convertedSubstrate())
            putEncryptedString(WalletSecretQuarantine.keyFor(ETHEREUM), "kept")
            putEncryptedString(
                WalletPublicIdentityRecovery.keyFor(META_ID, SUBSTRATE),
                WalletPublicIdentityRecovery.MARKER_VALUE
            )
        }
        val before = rawState(preferences)

        val report = revalidation(preferences).reconcile(ShippedWalletFixtures.database(malformed))

        assertEquals(0, report.markersCleared)
        assertEquals(before, rawState(preferences))
        val guard = WalletSecretAccessGuard(preferences)
        assertTrue(guard.isRecoveryRequired(META_ID, WalletSecretScope.Substrate))
        assertFalse(guard.isRecoveryRequired(META_ID, WalletSecretScope.Ton))
    }

    @Test
    fun aMarkerOnAnActiveLegacyPayloadIsClearedAndThePayloadConverts() {
        // The 3.7.4 raw-seed payload, still active because a marker stopped
        // its conversion.
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(ACCESS, wallet.accessSecretsHex)
            putEncryptedString(
                WalletPublicIdentityRecovery.keyFor(META_ID, ACCESS),
                WalletPublicIdentityRecovery.MARKER_VALUE
            )
        }

        val report = revalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))

        assertEquals(1, report.markersCleared)
        assertEquals(convertedSubstrate(), preferences.getDecryptedString(SUBSTRATE))
        assertFalse(preferences.hasKey(ACCESS))
        assertEquals(
            wallet.accessSecretsHex,
            preferences.getDecryptedString(WalletSecretArchive.keyFor(ACCESS))
        )
        assertFalse(WalletSecretAccessGuard(preferences).hasAnyRecoveryState())
    }

    @Test
    fun aMarkerWithNothingStoredForItIsCleared() {
        val markerKey = WalletPublicIdentityRecovery.keyFor(META_ID, "$META_ID:TON_SECRETS")
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(SUBSTRATE, convertedSubstrate())
            putEncryptedString(markerKey, WalletPublicIdentityRecovery.MARKER_VALUE)
        }

        val report = revalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))

        assertEquals(1, report.markersCleared)
        assertFalse(preferences.hasKey(markerKey))
        assertEquals(convertedSubstrate(), preferences.getDecryptedString(SUBSTRATE))
    }

    @Test
    fun aChainAccountMarkerWhoseRowAndKeyProveIsCleared() {
        val chainKeypair = otherKeypair()
        val accountId = chainKeypair.publicKey.substrateAccountId()
        val chainKey = "$META_ID:${accountId.toHexString()}:ACCESS_SECRETS"
        val chainSecret = ChainAccountSecrets(
            keyPair = chainKeypair,
            seed = OTHER_SEED_HEX.removePrefix("0x").fromHex()
        ).toHexString()
        val markerKey = WalletPublicIdentityRecovery.keyFor(META_ID, chainKey)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(SUBSTRATE, convertedSubstrate())
            putEncryptedString(chainKey, chainSecret)
            putEncryptedString(markerKey, WalletPublicIdentityRecovery.MARKER_VALUE)
        }
        val database = ShippedWalletFixtures.database(
            identity,
            chainAccounts = listOf(
                mapOf(
                    "metaId" to META_ID,
                    "accountId" to accountId,
                    "publicKey" to chainKeypair.publicKey,
                    "cryptoType" to CryptoType.ED25519.name
                )
            )
        )

        val report = revalidation(preferences).reconcile(database)

        assertEquals(1, report.markersCleared)
        assertFalse(preferences.hasKey(markerKey))
        assertEquals(chainSecret, preferences.getDecryptedString(chainKey))
    }

    @Test
    fun aStaleQuarantineNextToAProvingActiveKeyIsArchived() {
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(SUBSTRATE, convertedSubstrate())
            putEncryptedString(WalletSecretQuarantine.keyFor(SUBSTRATE), "older-copy")
        }

        val report = revalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))

        assertEquals(1, report.archived)
        assertEquals(convertedSubstrate(), preferences.getDecryptedString(SUBSTRATE))
        assertEquals("older-copy", preferences.getDecryptedString(WalletSecretArchive.keyFor(SUBSTRATE)))
        assertFalse(WalletSecretAccessGuard(preferences).hasAnyRecoveryState())
    }

    @Test
    fun legacyPayloadQuarantinedNextToAFailingV3EntryConvertsOnTheSameStart() {
        // What 76 -> 77 leaves when a V3 entry that proves nothing (here the
        // empty value an unreadable ciphertext decrypts to) sat next to a
        // legacy payload that proves the wallet: both quarantined, neither
        // overwritten.
        listOf("", "foreign-material").forEach { failing ->
            val preferences = HashMapEncryptedPreferences().apply {
                putEncryptedString(WalletSecretQuarantine.keyFor(ACCESS), wallet.accessSecretsHex)
                putEncryptedString(WalletSecretQuarantine.keyFor(SUBSTRATE), failing)
            }

            val report = revalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))

            assertEquals(1, report.released)
            assertEquals(1, report.archived)
            assertEquals(convertedSubstrate(), preferences.getDecryptedString(SUBSTRATE))
            assertEquals(
                wallet.accessSecretsHex,
                preferences.getDecryptedString(WalletSecretArchive.keyFor(ACCESS))
            )
            assertEquals(failing, preferences.getDecryptedString(WalletSecretArchive.keyFor(SUBSTRATE)))
            assertFalse(WalletSecretAccessGuard(preferences).hasAnyRecoveryState())
        }
    }

    @Test
    fun archivedKeyOfAnExistingWalletIsRestoredWhenItsActiveKeyIsMissing() {
        // Critique D-2: if a wallet row was unreadable when the orphan pass
        // ran, its keys come back once the row is readable again.
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(WalletSecretArchive.keyFor(SUBSTRATE), convertedSubstrate())
        }

        val report = revalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))

        assertEquals(1, report.restored)
        assertEquals(convertedSubstrate(), preferences.getDecryptedString(SUBSTRATE))
        assertFalse(preferences.hasKey(WalletSecretArchive.keyFor(SUBSTRATE)))
    }

    @Test
    fun archivedLegacyPayloadRecreatesAMissingKeyAndStaysArchived() {
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(WalletSecretArchive.keyFor(ACCESS), wallet.accessSecretsHex)
        }

        val report = revalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))

        assertEquals(1, report.restored)
        assertTrue(preferences.hasKey(SUBSTRATE))
        assertEquals(
            wallet.accessSecretsHex,
            preferences.getDecryptedString(WalletSecretArchive.keyFor(ACCESS))
        )
    }

    @Test
    fun archivesOfDeletedWalletsAndConvertedLegacyPayloadsAreLeftAlone() {
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(SUBSTRATE, convertedSubstrate())
            putEncryptedString(WalletSecretArchive.keyFor(ACCESS), wallet.accessSecretsHex)
            putEncryptedString(WalletSecretArchive.keyFor("9:SUBSTRATE_SECRETS"), "deleted")
        }
        val before = rawState(preferences)

        val report = revalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))

        assertEquals(WalletQuarantineRevalidationReport(), report)
        assertEquals(before, rawState(preferences))
    }

    @Test
    fun chainAccountQuarantineThatProvesItsRowMovesBack() {
        // 3.x importChainFromSeed: an ED25519 chain account with its own seed.
        val chainKeypair = otherKeypair()
        val accountId = chainKeypair.publicKey.substrateAccountId()
        val chainKey = "$META_ID:${accountId.toHexString()}:ACCESS_SECRETS"
        val chainSecret = ChainAccountSecrets(
            keyPair = chainKeypair,
            seed = OTHER_SEED_HEX.removePrefix("0x").fromHex()
        ).toHexString()
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(SUBSTRATE, convertedSubstrate())
            putEncryptedString(WalletSecretQuarantine.keyFor(chainKey), chainSecret)
        }
        val database = ShippedWalletFixtures.database(
            identity,
            chainAccounts = listOf(
                mapOf(
                    "metaId" to META_ID,
                    "accountId" to accountId,
                    "publicKey" to chainKeypair.publicKey,
                    "cryptoType" to CryptoType.ED25519.name
                )
            )
        )

        val report = revalidation(preferences).reconcile(database)

        assertEquals(1, report.released)
        assertEquals(chainSecret, preferences.getDecryptedString(chainKey))
        assertFalse(WalletSecretAccessGuard(preferences).hasAnyRecoveryState())
    }

    @Test
    fun orphanTonConnectQuarantineIsArchivedWithItsMarker() {
        val scopedKey = TonConnectStorageKeys.scoped(
            metaId = META_ID,
            url = "https://dapp.example",
            source = "WEB"
        )
        val markerKey = WalletPublicIdentityRecovery.keyFor(META_ID, scopedKey)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(SUBSTRATE, convertedSubstrate())
            putEncryptedString(WalletSecretQuarantine.keyFor(scopedKey), "session")
            putEncryptedString(markerKey, WalletPublicIdentityRecovery.MARKER_VALUE)
        }

        revalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))

        assertEquals("session", preferences.getDecryptedString(WalletSecretArchive.keyFor(scopedKey)))
        assertFalse(preferences.hasKey(markerKey))
        assertFalse(WalletSecretAccessGuard(preferences).hasAnyRecoveryState())
    }

    @Test
    fun aMalformedTonConnectionRowLeavesOnlyTonConnectItemsUntouched() {
        val scopedKey = TonConnectStorageKeys.scoped(
            metaId = META_ID,
            url = "https://dapp.example",
            source = "WEB"
        )
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(WalletSecretQuarantine.keyFor(ACCESS), wallet.accessSecretsHex)
            putEncryptedString(WalletSecretQuarantine.keyFor(scopedKey), "session")
        }
        val database = ShippedWalletFixtures.database(
            identity,
            tonConnections = listOf(
                mapOf("metaId" to 0L, "url" to "https://dapp.example", "source" to "WEB")
            )
        )

        val report = revalidation(preferences).reconcile(database)

        assertEquals(1, report.released)
        assertTrue(preferences.hasKey(SUBSTRATE))
        assertEquals("session", preferences.getDecryptedString(WalletSecretQuarantine.keyFor(scopedKey)))
        assertFalse(preferences.hasKey(WalletSecretArchive.keyFor(scopedKey)))
    }

    @Test
    fun secondStartChangesNothing() {
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(WalletSecretQuarantine.keyFor(ACCESS), wallet.accessSecretsHex)
            putEncryptedString(WalletSecretQuarantine.keyFor("9:TON_SECRETS"), "deleted")
        }
        val database = ShippedWalletFixtures.database(identity)
        revalidation(preferences).reconcile(database)
        val afterFirstStart = rawState(preferences)

        val report = revalidation(preferences).reconcile(database)

        assertEquals(WalletQuarantineRevalidationReport(), report)
        assertEquals(afterFirstStart, rawState(preferences))
    }

    @Test
    fun aConcurrentWriteSkipsTheItemWithoutWritingAnything() {
        val delegate = HashMapEncryptedPreferences().apply {
            putEncryptedString(WalletSecretQuarantine.keyFor(SUBSTRATE), convertedSubstrate())
        }
        val racing = object : EncryptedPreferences by delegate {
            override fun replaceEncryptedStringsDurablyIfStatesMatch(
                expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
                valuesToPut: Map<String, String>,
                keysToRemove: Set<String>,
                snapshotMoves: List<EncryptedPreferenceSnapshotMove>
            ): Boolean {
                // Another writer published the active key first.
                delegate.putEncryptedString(SUBSTRATE, "concurrent")
                return delegate.replaceEncryptedStringsDurablyIfStatesMatch(
                    expectedStates,
                    valuesToPut,
                    keysToRemove,
                    snapshotMoves
                )
            }
        }

        val report = revalidation(racing).reconcile(ShippedWalletFixtures.database(identity))

        assertEquals(1, report.skipped)
        assertEquals("concurrent", delegate.getDecryptedString(SUBSTRATE))
        assertEquals(
            convertedSubstrate(),
            delegate.getDecryptedString(WalletSecretQuarantine.keyFor(SUBSTRATE))
        )
    }

    @Test
    fun aPriorGlobalInventoryLimitDoesNotSuppressAnIndependentlyHealthyKey() {
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(WalletSecretQuarantine.keyFor(SUBSTRATE), convertedSubstrate())
            putEncryptedString(WalletSecretQuarantine.keyFor(ETHEREUM), "x")
        }
        val tightLimits = WalletOrphanSecretInventoryLimits(
            maximumWalletRows = 4,
            maximumChainAccountRows = 4,
            maximumTonConnectionRows = 4,
            maximumCandidateKeys = 1,
            maximumKeyBytes = 256,
            maximumTotalKeyBytes = 1_024
        )

        val report = WalletQuarantineRevalidation(
            encryptedPreferences = preferences,
            walletRootSecretValidation = WalletRootSecretValidator,
            chainAccountSecretValidation = ChainAccountSecretValidator,
            limits = tightLimits
        ).reconcile(ShippedWalletFixtures.database(identity))

        assertEquals(0, report.skipped)
        assertEquals(1, report.released)
        assertFalse(preferences.hasKey(WalletSecretQuarantine.keyFor(SUBSTRATE)))
        assertEquals(convertedSubstrate(), preferences.getDecryptedString(SUBSTRATE))
        assertEquals("x", preferences.getDecryptedString(WalletSecretQuarantine.keyFor(ETHEREUM)))
    }

    private fun revalidation(preferences: EncryptedPreferences) =
        WalletQuarantineRevalidation(preferences)

    private fun convertedSubstrate(): String {
        return checkNotNull(
            WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
                encoded = wallet.accessSecretsHex,
                identity = identity
            ).substratePlaintext
        )
    }

    private fun otherKeypair() = SubstrateKeypairFactory.generate(
        mapCryptoTypeToEncryption(CryptoType.ED25519),
        OTHER_SEED_HEX.removePrefix("0x").fromHex(),
        emptyList()
    )

    private fun rawState(preferences: HashMapEncryptedPreferences): Map<String, String?> {
        val keys = preferences.keysWithPrefixes(
            prefixes = setOf(
                "$META_ID:",
                "9:",
                WalletSecretQuarantine.KEY_PREFIX,
                WalletSecretArchive.KEY_PREFIX,
                WalletPublicIdentityRecovery.KEY_PREFIX
            ),
            maxResultCount = 64,
            maxKeyBytes = 512,
            maxTotalKeyBytes = 16_384
        )
        return keys.associateWith(preferences::getDecryptedString)
    }

    private companion object {
        const val META_ID = 5L
        const val ACCESS = "5:ACCESS_SECRETS"
        const val SUBSTRATE = "5:SUBSTRATE_SECRETS"
        const val ETHEREUM = "5:ETHEREUM_SECRETS"
    }
}

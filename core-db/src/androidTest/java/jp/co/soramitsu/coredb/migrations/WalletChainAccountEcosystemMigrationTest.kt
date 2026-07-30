package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryStateIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString as toPlainHexString
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WalletChainAccountEcosystemMigrationTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val createdDatabases = mutableSetOf<String>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun deleteDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
    }

    @Test
    fun ethereumChainAccountUsesTwentyByteAddressAndRemainsActive() {
        val fixture = ethereumFixture(1)
        createVersion76Database(
            databaseName = EVM_DATABASE,
            ecosystem = "Ethereum",
            publicKey = fixture.publicKey,
            accountId = fixture.address,
            insertChainAccount = true
        )
        val activeKey = chainSecretKey(fixture.address)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(activeKey, fixture.encoded)
        }

        migrateTo77(EVM_DATABASE, preferences)

        assertEquals(fixture.encoded, preferences.getDecryptedString(activeKey))
        assertFalse(
            preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
        )
        assertFalse(
            preferences.hasKey(
                WalletPublicIdentityRecovery.keyFor(META_ID, activeKey)
            )
        )
    }

    @Test
    fun substrateEcdsaStillUsesThirtyTwoByteAccountId() {
        val privateKey = ByteArray(32).also { it[it.lastIndex] = 2 }
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey)
        val accountId = keypair.publicKey.substrateAccountId()
        val encoded = ChainAccountSecrets(
            keyPair = keypair,
            seed = privateKey
        ).toHexString()
        createVersion76Database(
            databaseName = SUBSTRATE_ECDSA_DATABASE,
            ecosystem = "Substrate",
            publicKey = keypair.publicKey,
            accountId = accountId,
            insertChainAccount = true
        )
        val activeKey = chainSecretKey(accountId)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(activeKey, encoded)
        }

        migrateTo77(SUBSTRATE_ECDSA_DATABASE, preferences)

        assertEquals(encoded, preferences.getDecryptedString(activeKey))
        assertFalse(
            preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
        )
    }

    @Test
    fun substrateSr25519SecretUsesAndroidNativeProviderAndRemainsActive() {
        val seed = ByteArray(32) { (it + 1).toByte() }
        val keypair = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.SR25519,
            seed = seed,
            junctions = emptyList()
        )
        val accountId = keypair.publicKey.substrateAccountId()
        val encoded = ChainAccountSecrets(
            keyPair = keypair,
            seed = seed,
            derivationPath = ""
        ).toHexString()
        createVersion76Database(
            databaseName = SUBSTRATE_SR25519_DATABASE,
            ecosystem = "Substrate",
            publicKey = keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.SR25519,
            insertChainAccount = true
        )
        val activeKey = chainSecretKey(accountId)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(activeKey, encoded)
        }

        migrateTo77(SUBSTRATE_SR25519_DATABASE, preferences)

        assertEquals(encoded, preferences.getDecryptedString(activeKey))
        assertFalse(
            preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
        )
    }

    @Test
    fun orphanAccessSecretMovesExactlyToRecoveryQuarantine() {
        val fixture = ethereumFixture(3)
        createVersion76Database(
            databaseName = ORPHAN_DATABASE,
            ecosystem = "EthereumBased",
            publicKey = fixture.publicKey,
            accountId = fixture.address,
            insertChainAccount = false
        )
        val activeKey = chainSecretKey(fixture.address)
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        val markerKey = WalletPublicIdentityRecovery.keyFor(META_ID, activeKey)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(activeKey, fixture.encoded)
        }

        migrateTo77(ORPHAN_DATABASE, preferences)

        assertFalse(preferences.hasKey(activeKey))
        assertEquals(
            fixture.encoded,
            preferences.getDecryptedString(quarantineKey)
        )
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            preferences.getDecryptedString(markerKey)
        )
    }

    @Test
    fun orphanInventoryScansEveryBoundedWalletPrefixBatch() {
        val fixture = ethereumFixture(6)
        val orphanMetaId = 34L
        createVersion76Database(
            databaseName = BATCHED_ORPHAN_DATABASE,
            ecosystem = "EthereumBased",
            publicKey = fixture.publicKey,
            accountId = fixture.address,
            insertChainAccount = false,
            additionalWalletIds = 2L..orphanMetaId
        )
        val activeKey = chainSecretKey(
            accountId = fixture.address,
            metaId = orphanMetaId
        )
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        val markerKey = WalletPublicIdentityRecovery.keyFor(
            orphanMetaId,
            activeKey
        )
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(activeKey, fixture.encoded)
        }

        migrateTo77(BATCHED_ORPHAN_DATABASE, preferences)

        assertFalse(preferences.hasKey(activeKey))
        assertEquals(
            fixture.encoded,
            preferences.getDecryptedString(quarantineKey)
        )
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            preferences.getDecryptedString(markerKey)
        )
    }

    @Test
    fun unknownEcosystemMarksRecoveryWithoutMovingCiphertext() {
        val fixture = ethereumFixture(4)
        createVersion76Database(
            databaseName = UNKNOWN_ECOSYSTEM_DATABASE,
            ecosystem = "Alien",
            publicKey = fixture.publicKey,
            accountId = fixture.address,
            insertChainAccount = true
        )
        val activeKey = chainSecretKey(fixture.address)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(activeKey, fixture.encoded)
        }

        migrateTo77(UNKNOWN_ECOSYSTEM_DATABASE, preferences)

        assertEquals(fixture.encoded, preferences.getDecryptedString(activeKey))
        assertTrue(
            preferences.hasKey(
                WalletPublicIdentityRecovery.keyFor(META_ID, activeKey)
            )
        )
        assertFalse(
            preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
        )
    }

    @Test
    fun ethereumChainWithThirtyTwoByteAccountIdMarksRecovery() {
        val fixture = ethereumFixture(5)
        val wrongAccountId = ByteArray(32) { 9 }
        createVersion76Database(
            databaseName = WRONG_EVM_LENGTH_DATABASE,
            ecosystem = "EthereumBased",
            publicKey = fixture.publicKey,
            accountId = wrongAccountId,
            insertChainAccount = true
        )
        val activeKey = chainSecretKey(wrongAccountId)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(activeKey, fixture.encoded)
        }

        migrateTo77(WRONG_EVM_LENGTH_DATABASE, preferences)

        assertEquals(fixture.encoded, preferences.getDecryptedString(activeKey))
        assertTrue(
            preferences.hasKey(
                WalletPublicIdentityRecovery.keyFor(META_ID, activeKey)
            )
        )
        assertFalse(
            preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
        )
    }

    @Test
    fun lateWalletConflictLeavesEveryEarlierPreferenceUntouched() {
        val firstSeed = ByteArray(32) { (it + 31).toByte() }
        val secondSeed = ByteArray(32) { (it + 63).toByte() }
        val firstKeypair = substrateKeypair(firstSeed)
        val secondKeypair = substrateKeypair(secondSeed)
        createVersion76RootWallets(
            listOf(firstKeypair, secondKeypair)
        )
        val firstLegacyKey = "1:ACCESS_SECRETS"
        val firstTargetKey = "1:SUBSTRATE_SECRETS"
        val secondLegacyKey = "2:ACCESS_SECRETS"
        val secondTargetKey = "2:SUBSTRATE_SECRETS"
        val firstLegacy = MetaAccountSecrets(
            substrateKeyPair = firstKeypair,
            seed = firstSeed,
            substrateDerivationPath = ""
        ).toHexString()
        val secondLegacy = MetaAccountSecrets(
            substrateKeyPair = secondKeypair,
            seed = secondSeed,
            substrateDerivationPath = ""
        ).toHexString()
        val conflictingSecondTarget = "conflicting-late-wallet-target"
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(firstLegacyKey, firstLegacy)
            putEncryptedString(secondLegacyKey, secondLegacy)
            putEncryptedString(
                secondTargetKey,
                conflictingSecondTarget
            )
        }

        assertThrows(WalletSecretConcurrentMutationException::class.java) {
            migrateTo77(ROOT_CONFLICT_DATABASE, preferences)
        }

        assertEquals(
            firstLegacy,
            preferences.getDecryptedString(firstLegacyKey)
        )
        assertFalse(preferences.hasKey(firstTargetKey))
        assertEquals(
            secondLegacy,
            preferences.getDecryptedString(secondLegacyKey)
        )
        assertEquals(
            conflictingSecondTarget,
            preferences.getDecryptedString(secondTargetKey)
        )
    }

    @Test
    fun lateWalletQuarantineConflictLeavesEarlierWalletUntouched() {
        val firstSeed = ByteArray(32) { (it + 71).toByte() }
        val secondSeed = ByteArray(32) { (it + 103).toByte() }
        val firstKeypair = substrateKeypair(firstSeed)
        val secondKeypair = substrateKeypair(secondSeed)
        createVersion76RootWallets(
            keypairs = listOf(firstKeypair, secondKeypair),
            databaseName = ROOT_QUARANTINE_CONFLICT_DATABASE
        )
        val firstLegacyKey = "1:ACCESS_SECRETS"
        val firstTargetKey = "1:SUBSTRATE_SECRETS"
        val firstLegacy = MetaAccountSecrets(
            substrateKeyPair = firstKeypair,
            seed = firstSeed,
            substrateDerivationPath = ""
        ).toHexString()
        val lateActiveKey = "2:ACCESS_SECRETS"
        val lateQuarantineKey =
            WalletSecretQuarantine.keyFor(lateActiveKey)
        val lateCorruptSecret = "late-corrupt-wallet-secret"
        val conflictingQuarantine = "unrelated-quarantine-ciphertext"
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(firstLegacyKey, firstLegacy)
            putEncryptedString(lateActiveKey, lateCorruptSecret)
            putEncryptedString(
                lateQuarantineKey,
                conflictingQuarantine
            )
        }

        assertThrows(WalletSecretConcurrentMutationException::class.java) {
            migrateTo77(ROOT_QUARANTINE_CONFLICT_DATABASE, preferences)
        }

        assertEquals(
            firstLegacy,
            preferences.getDecryptedString(firstLegacyKey)
        )
        assertFalse(preferences.hasKey(firstTargetKey))
        assertEquals(
            lateCorruptSecret,
            preferences.getDecryptedString(lateActiveKey)
        )
        assertEquals(
            conflictingQuarantine,
            preferences.getDecryptedString(lateQuarantineKey)
        )
    }

    @Test
    fun orphanQuarantineConflictLeavesEarlierWalletUntouched() {
        val seed = ByteArray(32) { (it + 119).toByte() }
        val keypair = substrateKeypair(seed)
        createVersion76RootWallets(
            keypairs = listOf(keypair),
            databaseName = ORPHAN_QUARANTINE_CONFLICT_DATABASE
        )
        val rootKey = "1:ACCESS_SECRETS"
        val rootTargetKey = "1:SUBSTRATE_SECRETS"
        val rootSecret = MetaAccountSecrets(
            substrateKeyPair = keypair,
            seed = seed,
            substrateDerivationPath = ""
        ).toHexString()
        val orphanKey = chainSecretKey(ByteArray(20) { 0x44 })
        val orphanQuarantineKey = WalletSecretQuarantine.keyFor(orphanKey)
        val orphanSecret = "orphan-active-secret"
        val conflictingQuarantine = "conflicting-orphan-quarantine"
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(rootKey, rootSecret)
            putEncryptedString(orphanKey, orphanSecret)
            putEncryptedString(
                orphanQuarantineKey,
                conflictingQuarantine
            )
        }

        assertThrows(WalletSecretConcurrentMutationException::class.java) {
            migrateTo77(
                ORPHAN_QUARANTINE_CONFLICT_DATABASE,
                preferences
            )
        }

        assertEquals(rootSecret, preferences.getDecryptedString(rootKey))
        assertFalse(preferences.hasKey(rootTargetKey))
        assertEquals(orphanSecret, preferences.getDecryptedString(orphanKey))
        assertEquals(
            conflictingQuarantine,
            preferences.getDecryptedString(orphanQuarantineKey)
        )
    }

    @Test
    fun ownerlessDatabaseRecoversEveryRootChainAndScopedTonNamespace() {
        createEmptyDatabase(
            databaseName = OWNERLESS_ALL_NAMESPACES_DATABASE,
            version = 76
        )
        val activeSecrets = linkedMapOf(
            "41:ACCESS_SECRETS" to "legacy-root",
            "42:SUBSTRATE_SECRETS" to "substrate-root",
            "43:ETHEREUM_SECRETS" to "ethereum-root",
            "44:TON_SECRETS" to "ton-root",
            "45:${"ab".repeat(32)}:ACCESS_SECRETS" to "chain-root",
            TonConnectStorageKeys.scoped(
                metaId = 46L,
                url = "https://orphan.example/tonconnect-manifest.json",
                source = "WEB"
            ) to "ton-connect-private-key"
        )
        val preferences = HashMapEncryptedPreferences().apply {
            activeSecrets.forEach(::putEncryptedString)
        }

        migrateTo77(OWNERLESS_ALL_NAMESPACES_DATABASE, preferences)

        activeSecrets.forEach { (activeKey, expectedValue) ->
            assertOrphanRecovered(
                preferences = preferences,
                metaId = checkNotNull(
                    WalletActiveSecretKeyParser.parse(activeKey)
                ).metaId,
                activeKey = activeKey,
                expectedValue = expectedValue
            )
        }
    }

    @Test
    fun mismatchedRoomWalletIdCannotHideOwnerlessNamespaces() {
        val fixture = ethereumFixture(8)
        createVersion76Database(
            databaseName = MISMATCHED_OWNER_DATABASE,
            ecosystem = "Ethereum",
            publicKey = fixture.publicKey,
            accountId = fixture.address,
            insertChainAccount = false
        )
        val activeSecrets = linkedMapOf(
            "2:SUBSTRATE_SECRETS" to "mismatched-root",
            "2:${"cd".repeat(20)}:ACCESS_SECRETS" to "mismatched-chain",
            TonConnectStorageKeys.scoped(
                metaId = 2L,
                url = "https://mismatched.example",
                source = "QR"
            ) to "mismatched-ton-connect"
        )
        val preferences = HashMapEncryptedPreferences().apply {
            activeSecrets.forEach(::putEncryptedString)
        }

        migrateTo77(MISMATCHED_OWNER_DATABASE, preferences)

        activeSecrets.forEach { (activeKey, expectedValue) ->
            assertOrphanRecovered(
                preferences = preferences,
                metaId = 2L,
                activeKey = activeKey,
                expectedValue = expectedValue
            )
        }
    }

    @Test
    fun extantWalletWithoutExactTonRowCannotOwnScopedSecret() {
        val fixture = ethereumFixture(9)
        createVersion76Database(
            databaseName = EXTANT_TON_OWNER_DATABASE,
            ecosystem = "Ethereum",
            publicKey = fixture.publicKey,
            accountId = fixture.address,
            insertChainAccount = false
        )
        val activeKey = TonConnectStorageKeys.scoped(
            metaId = META_ID,
            url = "https://owned.example/manifest.json",
            source = "WEB"
        )
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(activeKey, "owned-ton-connect-secret")
        }

        migrateTo77(EXTANT_TON_OWNER_DATABASE, preferences)

        assertOrphanRecovered(
            preferences = preferences,
            metaId = META_ID,
            activeKey = activeKey,
            expectedValue = "owned-ton-connect-secret"
        )
    }

    @Test
    fun exactTonConnectionRowOwnsItsDerivedScopedSecret() {
        val fixture = ethereumFixture(10)
        val tonConnection = TonConnectionFixture(
            metaId = META_ID,
            url = "https://exact-owner.example/manifest.json",
            source = "WEB"
        )
        createVersion76Database(
            databaseName = EXACT_TON_OWNER_DATABASE,
            ecosystem = "Ethereum",
            publicKey = fixture.publicKey,
            accountId = fixture.address,
            insertChainAccount = false,
            tonConnection = tonConnection
        )
        val activeKey = TonConnectStorageKeys.scoped(
            metaId = tonConnection.metaId,
            url = tonConnection.url,
            source = tonConnection.source
        )
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(activeKey, "exact-row-ton-secret")
        }

        migrateTo77(EXACT_TON_OWNER_DATABASE, preferences)

        assertEquals(
            "exact-row-ton-secret",
            preferences.getDecryptedString(activeKey)
        )
        assertFalse(
            preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
        )
        assertFalse(
            preferences.hasKey(
                WalletPublicIdentityRecovery.keyFor(META_ID, activeKey)
            )
        )
    }

    @Test
    fun preferenceCommitThenRoomRollbackRetriesIdempotently() {
        createdDatabases += ORPHAN_ROLLBACK_RETRY_DATABASE
        val activeKey = "47:TON_SECRETS"
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(activeKey, "rollback-survivor")
        }

        helper.createDatabase(
            ORPHAN_ROLLBACK_RETRY_DATABASE,
            76
        ).use { database ->
            WalletSecretIntegrityMigration(preferences).migrate(database)
        }
        assertOrphanRecovered(
            preferences = preferences,
            metaId = 47L,
            activeKey = activeKey,
            expectedValue = "rollback-survivor"
        )

        migrateTo77(ORPHAN_ROLLBACK_RETRY_DATABASE, preferences)

        assertOrphanRecovered(
            preferences = preferences,
            metaId = 47L,
            activeKey = activeKey,
            expectedValue = "rollback-survivor"
        )
    }

    @Test
    fun ownerlessRootConflictPreventsEveryEarlierWalletMutation() {
        val seed = ByteArray(32) { (it + 151).toByte() }
        val keypair = substrateKeypair(seed)
        createVersion76RootWallets(
            keypairs = listOf(keypair),
            databaseName = OWNERLESS_ROOT_CONFLICT_DATABASE
        )
        val extantLegacyKey = "1:ACCESS_SECRETS"
        val extantTargetKey = "1:SUBSTRATE_SECRETS"
        val extantLegacy = MetaAccountSecrets(
            substrateKeyPair = keypair,
            seed = seed,
            substrateDerivationPath = ""
        ).toHexString()
        val orphanKey = "99:TON_SECRETS"
        val orphanValue = "ownerless-ton-secret"
        val conflictingQuarantine = "different-ciphertext"
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(extantLegacyKey, extantLegacy)
            putEncryptedString(orphanKey, orphanValue)
            putEncryptedString(
                WalletSecretQuarantine.keyFor(orphanKey),
                conflictingQuarantine
            )
        }

        assertThrows(WalletSecretConcurrentMutationException::class.java) {
            migrateTo77(OWNERLESS_ROOT_CONFLICT_DATABASE, preferences)
        }

        assertEquals(
            extantLegacy,
            preferences.getDecryptedString(extantLegacyKey)
        )
        assertFalse(preferences.hasKey(extantTargetKey))
        assertEquals(orphanValue, preferences.getDecryptedString(orphanKey))
        assertEquals(
            conflictingQuarantine,
            preferences.getDecryptedString(
                WalletSecretQuarantine.keyFor(orphanKey)
            )
        )
        assertFalse(
            preferences.hasKey(
                WalletPublicIdentityRecovery.keyFor(99L, orphanKey)
            )
        )
    }

    @Test
    fun currentVersionStartupInventoryRecoversHistoricalOrphans() {
        createdDatabases += CURRENT_VERSION_HISTORICAL_ORPHAN_DATABASE
        val activeSecrets = linkedMapOf(
            "51:SUBSTRATE_SECRETS" to "historical-substrate",
            "52:${"ef".repeat(20)}:ACCESS_SECRETS" to "historical-chain",
            TonConnectStorageKeys.scoped(
                metaId = 53L,
                url = "https://historical.example",
                source = "WEB"
            ) to "historical-ton-connect"
        )
        val preferences = HashMapEncryptedPreferences().apply {
            activeSecrets.forEach(::putEncryptedString)
        }

        helper.createDatabase(
            CURRENT_VERSION_HISTORICAL_ORPHAN_DATABASE,
            77
        ).use { database ->
            assertTrue(
                WalletOrphanSecretInventory(preferences)
                    .reconcile(database)
            )
        }

        activeSecrets.forEach { (activeKey, expectedValue) ->
            assertOrphanRecovered(
                preferences = preferences,
                metaId = checkNotNull(
                    WalletActiveSecretKeyParser.parse(activeKey)
                ).metaId,
                activeKey = activeKey,
                expectedValue = expectedValue
            )
        }
    }

    @Test
    fun pendingTonJournalDefersScopedInventoryUntilStartupReplay() {
        createEmptyDatabase(
            databaseName = PENDING_TON_JOURNAL_DATABASE,
            version = 76
        )
        val activeKey = TonConnectStorageKeys.scoped(
            metaId = 54L,
            url = "https://pending.example",
            source = "QR"
        )
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(activeKey, "journal-owned-ton-secret")
            putEncryptedString(
                TonConnectStorageKeys.MUTATION_JOURNAL_KEY,
                "opaque-journal-owned-by-ton-module"
            )
        }

        migrateTo77(PENDING_TON_JOURNAL_DATABASE, preferences)

        assertEquals(
            "journal-owned-ton-secret",
            preferences.getDecryptedString(activeKey)
        )
        assertFalse(
            preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
        )
    }

    @Test
    fun malformedSensitiveLookalikeFailsMigrationWithoutMovingCiphertext() {
        createEmptyDatabase(
            databaseName = MALFORMED_OWNERLESS_NAMESPACE_DATABASE,
            version = 76
        )
        val malformedKey = "0:SUBSTRATE_SECRETS"
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(malformedKey, "uncertain-private-data")
        }

        assertThrows(WalletRecoveryStateIntegrityException::class.java) {
            migrateTo77(
                MALFORMED_OWNERLESS_NAMESPACE_DATABASE,
                preferences
            )
        }

        assertEquals(
            "uncertain-private-data",
            preferences.getDecryptedString(malformedKey)
        )
        assertFalse(
            preferences.hasKey(
                WalletSecretQuarantine.keyFor(malformedKey)
            )
        )
    }

    @Test
    fun pendingWalletDeletionJournalRetainsItsOwnerlessActiveSecret() {
        createEmptyDatabase(
            databaseName = PENDING_WALLET_DELETE_JOURNAL_DATABASE,
            version = 76
        )
        val metaId = 55L
        val activeKey = "$metaId:TON_SECRETS"
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(activeKey, "pending-delete-secret")
        }
        val journalStore = WalletSecretMutationJournalStore(preferences)
        val beforeImage =
            WalletSecretMutationJournalStore.PublicAfterImage(
                name = "Pending deletion",
                substratePublicKeyHex = null,
                substrateAccountIdHex = null,
                substrateCryptoType = null,
                ethereumPublicKeyHex = null,
                ethereumAddressHex = null,
                tonPublicKeyHex = null,
                isSelected = true,
                position = 0,
                isBackedUp = false,
                googleBackupAddress = null,
                initialized = true
            )
        val keysToRemove = journalStore.deletionSecretKeys(
            metaId = metaId,
            beforeImage = beforeImage,
            chainAccountIdsHex = emptySet()
        )
        journalStore.stage(
            journal = WalletSecretMutationJournalStore.Journal(
                operationId = "12345678-1234-4123-8123-123456789abc",
                operation =
                WalletSecretMutationJournalStore.Operation.DELETE,
                metaId = metaId,
                beforeImage = beforeImage,
                afterImage = null,
                selectedMetaIdAfterDelete = null,
                chainAccountIdsHex = emptySet(),
                secretKeysToPut = emptySet(),
                secretKeysToRemove = keysToRemove
            ),
            finalSecretPlaintexts = emptyMap()
        )

        migrateTo77(
            PENDING_WALLET_DELETE_JOURNAL_DATABASE,
            preferences
        )

        assertEquals(
            "pending-delete-secret",
            preferences.getDecryptedString(activeKey)
        )
        assertFalse(
            preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
        )
        assertTrue(
            preferences.hasKey(
                WalletSecretMutationJournalStore.JOURNAL_KEY
            )
        )
    }

    @Test
    fun malformedTonConnectionIdentitiesFailClosedBeforeSecretMutation() {
        val scenarios = listOf(
            RawTonConnectionFixture(
                databaseName = MALFORMED_TON_META_ID_DATABASE,
                metaId = "not-an-integer",
                url = "https://valid.example",
                source = "WEB",
                disableForeignKeys = true
            ),
            RawTonConnectionFixture(
                databaseName = OVERSIZED_TON_URL_DATABASE,
                metaId = META_ID,
                url = "x".repeat(TonConnectStorageKeys.MAX_URL_BYTES + 1),
                source = "WEB"
            ),
            RawTonConnectionFixture(
                databaseName = INVALID_TON_SOURCE_DATABASE,
                metaId = META_ID,
                url = "https://valid.example",
                source = "NFC"
            )
        )

        scenarios.forEach { scenario ->
            createdDatabases += scenario.databaseName
            val activeKey = "99:TON_SECRETS"
            val preferences = HashMapEncryptedPreferences().apply {
                putEncryptedString(activeKey, scenario.databaseName)
            }
            helper.createDatabase(
                scenario.databaseName,
                77
            ).use { database ->
                database.insertWalletOwner(META_ID)
                if (scenario.disableForeignKeys) {
                    database.execSQL("PRAGMA foreign_keys = OFF")
                }
                database.insertRawTonConnection(scenario)

                assertThrows(
                    WalletPublicIdentityIntegrityException::class.java
                ) {
                    WalletOrphanSecretInventory(preferences)
                        .reconcile(database)
                }
            }

            assertEquals(
                scenario.databaseName,
                preferences.getDecryptedString(activeKey)
            )
            assertFalse(
                preferences.hasKey(
                    WalletSecretQuarantine.keyFor(activeKey)
                )
            )
        }
    }

    @Test
    fun tonConnectionRowLimitAcceptsExactBoundaryAndRejectsPlusOne() {
        val exactDatabaseName = EXACT_TON_ROW_LIMIT_DATABASE
        createdDatabases += exactDatabaseName
        val exactConnection = TonConnectionFixture(
            metaId = META_ID,
            url = "https://row-limit.example/one",
            source = "WEB"
        )
        val exactActiveKey = TonConnectStorageKeys.scoped(
            metaId = exactConnection.metaId,
            url = exactConnection.url,
            source = exactConnection.source
        )
        val exactPreferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(exactActiveKey, "exact-row-secret")
        }
        helper.createDatabase(exactDatabaseName, 77).use { database ->
            database.insertWalletOwner(META_ID)
            database.insertTonConnection(exactConnection)

            assertFalse(
                WalletOrphanSecretInventory(
                    encryptedPreferences = exactPreferences,
                    limits = oneTonRowLimits()
                ).reconcile(database)
            )
        }
        assertEquals(
            "exact-row-secret",
            exactPreferences.getDecryptedString(exactActiveKey)
        )

        val excessiveDatabaseName = EXCESSIVE_TON_ROW_LIMIT_DATABASE
        createdDatabases += excessiveDatabaseName
        val orphanKey = "99:TON_SECRETS"
        val excessivePreferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(orphanKey, "must-remain-active")
        }
        helper.createDatabase(excessiveDatabaseName, 77).use { database ->
            database.insertWalletOwner(META_ID)
            database.insertTonConnection(exactConnection)
            database.insertTonConnection(
                TonConnectionFixture(
                    metaId = META_ID,
                    url = "https://row-limit.example/two",
                    source = "QR"
                )
            )

            assertThrows(
                WalletPublicIdentityIntegrityException::class.java
            ) {
                WalletOrphanSecretInventory(
                    encryptedPreferences = excessivePreferences,
                    limits = oneTonRowLimits()
                ).reconcile(database)
            }
        }
        assertEquals(
            "must-remain-active",
            excessivePreferences.getDecryptedString(orphanKey)
        )
        assertFalse(
            excessivePreferences.hasKey(
                WalletSecretQuarantine.keyFor(orphanKey)
            )
        )
    }

    private fun createVersion76Database(
        databaseName: String,
        ecosystem: String,
        publicKey: ByteArray,
        accountId: ByteArray,
        cryptoType: CryptoType = CryptoType.ECDSA,
        insertChainAccount: Boolean,
        additionalWalletIds: Iterable<Long> = emptyList(),
        tonConnection: TonConnectionFixture? = null
    ) {
        createdDatabases += databaseName
        helper.createDatabase(databaseName, 76).apply {
            execSQL(
                """
                INSERT INTO meta_accounts(
                    id,
                    name,
                    isSelected,
                    position,
                    isBackedUp,
                    initialized
                ) VALUES(?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(META_ID, "Chain owner", 1, 0, 1, 1)
            )
            additionalWalletIds.forEach { walletId ->
                execSQL(
                    """
                    INSERT INTO meta_accounts(
                        id,
                        name,
                        isSelected,
                        position,
                        isBackedUp,
                        initialized
                    ) VALUES(?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(
                        walletId,
                        "Additional owner $walletId",
                        0,
                        walletId,
                        1,
                        1
                    )
                )
            }
            execSQL(
                """
                INSERT INTO chains(
                    id,
                    name,
                    icon,
                    prefix,
                    isEthereumBased,
                    isTestNet,
                    hasCrowdloans,
                    supportStakingPool,
                    isEthereumChain,
                    isChainlinkProvider,
                    supportNft,
                    isUsesAppId,
                    ecosystem
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    CHAIN_ID,
                    "Chain fixture",
                    "fixture-icon",
                    42,
                    if (ecosystem.startsWith("Ethereum")) 1 else 0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    ecosystem
                )
            )
            if (insertChainAccount) {
                insertChainAccount(publicKey, accountId, cryptoType)
            }
            tonConnection?.let { connection ->
                insertTonConnection(connection)
            }
            close()
        }
    }

    private fun createEmptyDatabase(
        databaseName: String,
        version: Int
    ) {
        createdDatabases += databaseName
        helper.createDatabase(databaseName, version).close()
    }

    private fun createVersion76RootWallets(
        keypairs: List<jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair>,
        databaseName: String = ROOT_CONFLICT_DATABASE
    ) {
        createdDatabases += databaseName
        helper.createDatabase(databaseName, 76).apply {
            keypairs.forEachIndexed { index, keypair ->
                val metaId = (index + 1).toLong()
                execSQL(
                    """
                    INSERT INTO meta_accounts(
                        id,
                        substratePublicKey,
                        substrateCryptoType,
                        substrateAccountId,
                        name,
                        isSelected,
                        position,
                        isBackedUp,
                        initialized
                    ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any>(
                        metaId,
                        keypair.publicKey,
                        CryptoType.SR25519.name,
                        keypair.publicKey.substrateAccountId(),
                        "Root wallet $metaId",
                        if (index == 0) 1 else 0,
                        index,
                        1,
                        1
                    )
                )
            }
            close()
        }
    }

    private fun substrateKeypair(
        seed: ByteArray
    ): jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair {
        return SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.SR25519,
            seed = seed,
            junctions = emptyList()
        )
    }

    private fun SupportSQLiteDatabase.insertChainAccount(
        publicKey: ByteArray,
        accountId: ByteArray,
        cryptoType: CryptoType
    ) {
        execSQL(
            """
            INSERT INTO chain_accounts(
                metaId,
                chainId,
                publicKey,
                accountId,
                cryptoType,
                name,
                initialized
            ) VALUES(?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                META_ID,
                CHAIN_ID,
                publicKey,
                accountId,
                cryptoType.name,
                "Signing account",
                1
            )
        )
    }

    private fun SupportSQLiteDatabase.insertTonConnection(
        connection: TonConnectionFixture
    ) {
        execSQL(
            """
            INSERT INTO ton_connection(
                metaId,
                clientId,
                name,
                icon,
                url,
                source
            ) VALUES(?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                connection.metaId,
                "ab".repeat(16),
                "TON connection",
                "https://exact-owner.example/icon.png",
                connection.url,
                connection.source
            )
        )
    }

    private fun SupportSQLiteDatabase.insertWalletOwner(metaId: Long) {
        execSQL(
            """
            INSERT INTO meta_accounts(
                id,
                name,
                isSelected,
                position,
                isBackedUp,
                initialized
            ) VALUES(?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                metaId,
                "TON owner $metaId",
                1,
                0,
                1,
                1
            )
        )
    }

    private fun SupportSQLiteDatabase.insertRawTonConnection(
        connection: RawTonConnectionFixture
    ) {
        execSQL(
            """
            INSERT INTO ton_connection(
                metaId,
                clientId,
                name,
                icon,
                url,
                source
            ) VALUES(?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                connection.metaId,
                "cd".repeat(16),
                "Raw TON connection",
                "https://invalid.example/icon.png",
                connection.url,
                connection.source
            )
        )
    }

    private fun oneTonRowLimits() =
        WalletOrphanSecretInventoryLimits(
            maximumWalletRows = 4,
            maximumChainAccountRows = 4,
            maximumTonConnectionRows = 1,
            maximumCandidateKeys = 16,
            maximumKeyBytes = 512,
            maximumTotalKeyBytes = 8_192
        )

    private fun migrateTo77(
        databaseName: String,
        preferences: HashMapEncryptedPreferences
    ) {
        helper.runMigrationsAndValidate(
            databaseName,
            77,
            true,
            WalletSecretIntegrityMigration(preferences)
        ).close()
    }

    private fun ethereumFixture(variant: Int): EthereumFixture {
        val privateKey = ByteArray(32).also {
            it[it.lastIndex] = variant.toByte()
        }
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey)
        return EthereumFixture(
            publicKey = keypair.publicKey,
            address = keypair.publicKey.ethereumAddressFromPublicKey(),
            encoded = ChainAccountSecrets(
                keyPair = keypair,
                seed = privateKey
            ).toHexString()
        )
    }

    private fun chainSecretKey(
        accountId: ByteArray,
        metaId: Long = META_ID
    ): String {
        return "$metaId:${accountId.toPlainHexString()}:ACCESS_SECRETS"
    }

    private fun assertOrphanRecovered(
        preferences: HashMapEncryptedPreferences,
        metaId: Long,
        activeKey: String,
        expectedValue: String
    ) {
        assertFalse(preferences.hasKey(activeKey))
        assertEquals(
            expectedValue,
            preferences.getDecryptedString(
                WalletSecretQuarantine.keyFor(activeKey)
            )
        )
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            preferences.getDecryptedString(
                WalletPublicIdentityRecovery.keyFor(metaId, activeKey)
            )
        )
    }

    private data class EthereumFixture(
        val publicKey: ByteArray,
        val address: ByteArray,
        val encoded: String
    )

    private data class TonConnectionFixture(
        val metaId: Long,
        val url: String,
        val source: String
    )

    private data class RawTonConnectionFixture(
        val databaseName: String,
        val metaId: Any,
        val url: String,
        val source: String,
        val disableForeignKeys: Boolean = false
    )

    private companion object {
        const val META_ID = 1L
        const val CHAIN_ID = "chain-fixture"
        const val EVM_DATABASE = "wallet-chain-ecosystem-evm"
        const val SUBSTRATE_ECDSA_DATABASE =
            "wallet-chain-ecosystem-substrate-ecdsa"
        const val SUBSTRATE_SR25519_DATABASE =
            "wallet-chain-ecosystem-substrate-sr25519"
        const val ORPHAN_DATABASE = "wallet-chain-ecosystem-orphan"
        const val BATCHED_ORPHAN_DATABASE =
            "wallet-chain-ecosystem-batched-orphan"
        const val UNKNOWN_ECOSYSTEM_DATABASE =
            "wallet-chain-ecosystem-unknown"
        const val WRONG_EVM_LENGTH_DATABASE =
            "wallet-chain-ecosystem-wrong-evm-length"
        const val ROOT_CONFLICT_DATABASE =
            "wallet-chain-ecosystem-root-conflict"
        const val ROOT_QUARANTINE_CONFLICT_DATABASE =
            "wallet-chain-ecosystem-root-quarantine-conflict"
        const val ORPHAN_QUARANTINE_CONFLICT_DATABASE =
            "wallet-chain-ecosystem-orphan-quarantine-conflict"
        const val OWNERLESS_ALL_NAMESPACES_DATABASE =
            "wallet-chain-ecosystem-ownerless-all"
        const val MISMATCHED_OWNER_DATABASE =
            "wallet-chain-ecosystem-mismatched-owner"
        const val EXTANT_TON_OWNER_DATABASE =
            "wallet-chain-ecosystem-extant-ton-owner"
        const val EXACT_TON_OWNER_DATABASE =
            "wallet-chain-ecosystem-exact-ton-owner"
        const val ORPHAN_ROLLBACK_RETRY_DATABASE =
            "wallet-chain-ecosystem-orphan-rollback-retry"
        const val OWNERLESS_ROOT_CONFLICT_DATABASE =
            "wallet-chain-ecosystem-ownerless-root-conflict"
        const val CURRENT_VERSION_HISTORICAL_ORPHAN_DATABASE =
            "wallet-chain-ecosystem-current-historical-orphan"
        const val PENDING_TON_JOURNAL_DATABASE =
            "wallet-chain-ecosystem-pending-ton-journal"
        const val MALFORMED_OWNERLESS_NAMESPACE_DATABASE =
            "wallet-chain-ecosystem-malformed-ownerless"
        const val PENDING_WALLET_DELETE_JOURNAL_DATABASE =
            "wallet-chain-ecosystem-pending-wallet-delete"
        const val MALFORMED_TON_META_ID_DATABASE =
            "wallet-chain-ecosystem-malformed-ton-meta"
        const val OVERSIZED_TON_URL_DATABASE =
            "wallet-chain-ecosystem-oversized-ton-url"
        const val INVALID_TON_SOURCE_DATABASE =
            "wallet-chain-ecosystem-invalid-ton-source"
        const val EXACT_TON_ROW_LIMIT_DATABASE =
            "wallet-chain-ecosystem-exact-ton-row-limit"
        const val EXCESSIVE_TON_ROW_LIMIT_DATABASE =
            "wallet-chain-ecosystem-excessive-ton-row-limit"
    }
}

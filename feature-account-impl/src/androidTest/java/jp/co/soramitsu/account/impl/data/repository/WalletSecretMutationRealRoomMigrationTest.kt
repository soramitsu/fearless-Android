package jp.co.soramitsu.account.impl.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.security.MessageDigest
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.Journal
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.Operation
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.PublicAfterImage
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.SubstrateCryptoType
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises wallet-secret mutation recovery with the same Room, SharedPreferences,
 * Android Keystore, [EncryptionUtil], and [EncryptedPreferencesImpl] used by the app.
 *
 * Every recovery boundary closes Room, drops its process singleton, and constructs
 * fresh preference/encryption/store/coordinator objects. The only direct preference
 * writes are deliberate crash/fault fixtures.
 */
@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class WalletSecretMutationRealRoomMigrationTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private var openedDatabase: AppDatabase? = null

    @Before
    fun cleanStorage() {
        clearTestStorage()
    }

    @After
    fun closeAndCleanStorage() {
        clearTestStorage()
    }

    @Test
    fun stagedSecretBeforeDatabaseChangeSurvivesMigrationAndRepeatedRestarts() = runBlocking {
        val fixture = prepareInterruptedAddEvm()
        val exactStagedStorage = walletStorageSnapshot()

        val migrationStack = newStorageStack()
        val migratedDatabase = openProductionDatabase(migrationStack)
        val migratedDao = migratedDatabase.metaAccountDao()
        assertEquals(
            TARGET_DATABASE_VERSION,
            migratedDatabase.openHelper.writableDatabase.version
        )
        assertQuickCheck(migratedDatabase.openHelper.writableDatabase)
        assertOriginalWallet(
            account = requireNotNull(migratedDao.getMetaAccount(META_ID)),
            fixture = fixture
        )
        assertEthereumNotPublished(
            requireNotNull(migratedDao.getMetaAccount(META_ID))
        )
        assertEquals(exactStagedStorage, walletStorageSnapshot())
        assertTrue(
            migrationStack.encryptedPreferences.hasKey(
                WalletSecretMutationJournalStore.JOURNAL_KEY
            )
        )
        closeDatabaseForRestart()

        val replayStack = newStorageStack()
        val replayDatabase = openProductionDatabase(replayStack)
        val replayDao = replayDatabase.metaAccountDao()
        newCoordinator(replayDatabase, replayStack).reconcilePendingMutation()

        val reconciled = requireNotNull(replayDao.getMetaAccount(META_ID))
        assertOriginalWallet(reconciled, fixture)
        assertEthereumPublished(reconciled, fixture)
        assertReadableSecrets(replayStack, reconciled, fixture)
        assertFalse(
            replayStack.encryptedPreferences.hasKey(
                WalletSecretMutationJournalStore.JOURNAL_KEY
            )
        )
        assertNoQuarantine(replayStack.encryptedPreferences)
        assertQuickCheck(replayDatabase.openHelper.writableDatabase)
        val settledStorage = walletStorageSnapshot()
        closeDatabaseForRestart()

        val secondReplayStack = newStorageStack()
        val secondReplayDatabase = openProductionDatabase(secondReplayStack)
        val secondReplayDao = secondReplayDatabase.metaAccountDao()
        newCoordinator(secondReplayDatabase, secondReplayStack).reconcilePendingMutation()

        val replayedAgain = requireNotNull(secondReplayDao.getMetaAccount(META_ID))
        assertOriginalWallet(replayedAgain, fixture)
        assertEthereumPublished(replayedAgain, fixture)
        assertReadableSecrets(secondReplayStack, replayedAgain, fixture)
        assertEquals(settledStorage, walletStorageSnapshot())
        assertFalse(
            secondReplayStack.encryptedPreferences.hasKey(
                WalletSecretMutationJournalStore.JOURNAL_KEY
            )
        )
        assertQuickCheck(secondReplayDatabase.openHelper.writableDatabase)
        assertSourceFixtureHashes()
    }

    @Test
    fun successfulReplayClearsOnlyJournalOwnedStaleMarkerFromPriorBuild() = runBlocking {
        val fixture = prepareInterruptedAddEvm()
        val ethereumMarkerKey = WalletPublicIdentityRecovery.keyFor(
            metaId = META_ID,
            activeSecretKey = ETHEREUM_SECRET_KEY
        )
        val unrelatedSubstrateMarkerKey = WalletPublicIdentityRecovery.keyFor(
            metaId = META_ID,
            activeSecretKey = SUBSTRATE_SECRET_KEY
        )

        val migrationStack = newStorageStack()
        val migrationDatabase = openProductionDatabase(migrationStack)
        assertEquals(
            TARGET_DATABASE_VERSION,
            migrationDatabase.openHelper.writableDatabase.version
        )
        assertFalse(migrationStack.encryptedPreferences.hasKey(ethereumMarkerKey))

        // Reproduce the exact durable residue left by the previously broken
        // v76 -> v77 attempt. An unrelated marker must never be cleared merely
        // because it belongs to the same wallet.
        migrationStack.encryptedPreferences.replaceEncryptedStringsDurably(
            valuesToPut = mapOf(
                ethereumMarkerKey to WalletPublicIdentityRecovery.MARKER_VALUE,
                unrelatedSubstrateMarkerKey to
                    WalletPublicIdentityRecovery.MARKER_VALUE
            ),
            keysToRemove = emptySet()
        )
        closeDatabaseForRestart()

        val replayStack = newStorageStack()
        val replayDatabase = openProductionDatabase(replayStack)
        val replayDao = replayDatabase.metaAccountDao()
        newCoordinator(replayDatabase, replayStack).reconcilePendingMutation()

        val reconciled = requireNotNull(replayDao.getMetaAccount(META_ID))
        assertOriginalWallet(reconciled, fixture)
        assertEthereumPublished(reconciled, fixture)
        assertReadableSecrets(replayStack, reconciled, fixture)
        assertFalse(
            replayStack.encryptedPreferences.hasKey(
                WalletSecretMutationJournalStore.JOURNAL_KEY
            )
        )
        assertFalse(replayStack.encryptedPreferences.hasKey(ethereumMarkerKey))
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            replayStack.encryptedPreferences.getDecryptedString(
                unrelatedSubstrateMarkerKey
            )
        )
        assertNoQuarantine(replayStack.encryptedPreferences)
        assertQuickCheck(replayDatabase.openHelper.writableDatabase)
        val settledStorage = walletStorageSnapshot()
        closeDatabaseForRestart()

        val repeatedReplayStack = newStorageStack()
        val repeatedReplayDatabase = openProductionDatabase(repeatedReplayStack)
        newCoordinator(
            repeatedReplayDatabase,
            repeatedReplayStack
        ).reconcilePendingMutation()

        assertEquals(settledStorage, walletStorageSnapshot())
        assertFalse(
            repeatedReplayStack.encryptedPreferences.hasKey(ethereumMarkerKey)
        )
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            repeatedReplayStack.encryptedPreferences.getDecryptedString(
                unrelatedSubstrateMarkerKey
            )
        )
        assertQuickCheck(repeatedReplayDatabase.openHelper.writableDatabase)
    }

    @Test
    fun databaseCommitBeforeJournalClearPreservesNewerSelectionAndMetadata() = runBlocking {
        val fixture = prepareInterruptedAddEvm(includeSecondWallet = true)

        val migrationStack = newStorageStack()
        val migrationDatabase = openProductionDatabase(migrationStack)
        assertEquals(
            TARGET_DATABASE_VERSION,
            migrationDatabase.openHelper.writableDatabase.version
        )
        closeDatabaseForRestart()

        val commitStack = newStorageStack()
        val commitDatabase = openProductionDatabase(commitStack)
        val commitDao = commitDatabase.metaAccountDao()
        val beforeCommit = requireNotNull(commitDao.getMetaAccount(META_ID))
        val postCommitImage = MetaAccountLocal(
            substratePublicKey = beforeCommit.substratePublicKey?.clone(),
            substrateCryptoType = beforeCommit.substrateCryptoType,
            substrateAccountId = beforeCommit.substrateAccountId?.clone(),
            ethereumPublicKey = fixture.ethereumPublicKey.clone(),
            ethereumAddress = fixture.ethereumAddress.clone(),
            tonPublicKey = beforeCommit.tonPublicKey?.clone(),
            name = POST_COMMIT_WALLET_NAME,
            isSelected = false,
            position = POST_COMMIT_POSITION,
            isBackedUp = true,
            googleBackupAddress = POST_COMMIT_BACKUP_ADDRESS,
            initialized = false
        ).apply {
            id = META_ID
        }

        commitDatabase.withTransaction {
            commitDao.updateMetaAccount(postCommitImage)
            commitDao.selectMetaAccount(SECOND_META_ID)
        }
        assertPostCommitState(commitDao, fixture)
        assertTrue(
            commitStack.encryptedPreferences.hasKey(
                WalletSecretMutationJournalStore.JOURNAL_KEY
            )
        )
        closeDatabaseForRestart()

        val recoveryStack = newStorageStack()
        val recoveryDatabase = openProductionDatabase(recoveryStack)
        val recoveryDao = recoveryDatabase.metaAccountDao()
        newCoordinator(recoveryDatabase, recoveryStack).reconcilePendingMutation()

        assertPostCommitState(recoveryDao, fixture)
        assertReadableSecrets(
            stack = recoveryStack,
            account = requireNotNull(recoveryDao.getMetaAccount(META_ID)),
            fixture = fixture
        )
        assertFalse(
            recoveryStack.encryptedPreferences.hasKey(
                WalletSecretMutationJournalStore.JOURNAL_KEY
            )
        )
        assertNoQuarantine(recoveryStack.encryptedPreferences)
        val settledStorage = walletStorageSnapshot()
        closeDatabaseForRestart()

        val repeatedReplayStack = newStorageStack()
        val repeatedReplayDatabase = openProductionDatabase(repeatedReplayStack)
        val repeatedReplayDao = repeatedReplayDatabase.metaAccountDao()
        newCoordinator(
            repeatedReplayDatabase,
            repeatedReplayStack
        ).reconcilePendingMutation()

        assertPostCommitState(repeatedReplayDao, fixture)
        assertReadableSecrets(
            stack = repeatedReplayStack,
            account = requireNotNull(repeatedReplayDao.getMetaAccount(META_ID)),
            fixture = fixture
        )
        assertEquals(settledStorage, walletStorageSnapshot())
        assertQuickCheck(repeatedReplayDatabase.openHelper.writableDatabase)
    }

    @Test
    fun temporaryWrappedKeyFailurePreservesExactStateAndSucceedsOnRetry() = runBlocking {
        val fixture = prepareInterruptedAddEvm()
        val exactStagedStorage = walletStorageSnapshot()
        val exactWrappedKey = checkNotNull(
            keyPreferences().getString(WRAPPED_AES_KEY_FIELD, null)
        )
        assertTrue(
            keyPreferences().edit()
                .putString(WRAPPED_AES_KEY_FIELD, MALFORMED_WRAPPED_KEY)
                .commit()
        )

        val failure = runCatching {
            openProductionDatabase(newStorageStack())
        }.exceptionOrNull()
        assertNotNull("Expected the temporary secure-storage failure", failure)
        assertTrue(
            "Expected WalletSecureStorageUnavailableException in $failure",
            failure.hasCause<WalletSecureStorageUnavailableException>()
        )
        closeDatabaseForRestart()

        assertEquals(SOURCE_DATABASE_VERSION, rawDatabaseVersion())
        assertRawWalletHasNoEthereumIdentity()
        assertEquals(exactStagedStorage, walletStorageSnapshot())
        assertEquals(
            MALFORMED_WRAPPED_KEY,
            keyPreferences().getString(WRAPPED_AES_KEY_FIELD, null)
        )

        assertTrue(
            keyPreferences().edit()
                .putString(WRAPPED_AES_KEY_FIELD, exactWrappedKey)
                .commit()
        )

        val retryMigrationStack = newStorageStack()
        val retryMigrationDatabase = openProductionDatabase(retryMigrationStack)
        assertEquals(
            TARGET_DATABASE_VERSION,
            retryMigrationDatabase.openHelper.writableDatabase.version
        )
        assertOriginalWallet(
            account = requireNotNull(
                retryMigrationDatabase.metaAccountDao().getMetaAccount(META_ID)
            ),
            fixture = fixture
        )
        assertEthereumNotPublished(
            requireNotNull(
                retryMigrationDatabase.metaAccountDao().getMetaAccount(META_ID)
            )
        )
        closeDatabaseForRestart()

        val retryReplayStack = newStorageStack()
        val retryReplayDatabase = openProductionDatabase(retryReplayStack)
        val retryReplayDao = retryReplayDatabase.metaAccountDao()
        newCoordinator(
            retryReplayDatabase,
            retryReplayStack
        ).reconcilePendingMutation()

        val recovered = requireNotNull(retryReplayDao.getMetaAccount(META_ID))
        assertOriginalWallet(recovered, fixture)
        assertEthereumPublished(recovered, fixture)
        assertReadableSecrets(retryReplayStack, recovered, fixture)
        assertFalse(
            retryReplayStack.encryptedPreferences.hasKey(
                WalletSecretMutationJournalStore.JOURNAL_KEY
            )
        )
        assertNoQuarantine(retryReplayStack.encryptedPreferences)
        assertQuickCheck(retryReplayDatabase.openHelper.writableDatabase)
    }

    @Test
    fun malformedStagedCiphertextNeverPublishesIdentityAcrossRestarts() = runBlocking {
        val fixture = prepareInterruptedAddEvm()

        val migrationStack = newStorageStack()
        val migrationDatabase = openProductionDatabase(migrationStack)
        assertEquals(
            TARGET_DATABASE_VERSION,
            migrationDatabase.openHelper.writableDatabase.version
        )
        closeDatabaseForRestart()

        val exactJournalCiphertext = checkNotNull(
            walletPreferences().getString(
                WalletSecretMutationJournalStore.JOURNAL_KEY,
                null
            )
        )
        assertTrue(
            walletPreferences().edit()
                .putString(ETHEREUM_SECRET_KEY, MALFORMED_STAGED_CIPHERTEXT)
                .commit()
        )

        repeat(MALFORMED_RESTART_ATTEMPTS) {
            val stack = newStorageStack()
            val database = openProductionDatabase(stack)
            val dao = database.metaAccountDao()
            val failure = runCatching {
                newCoordinator(database, stack).reconcilePendingMutation()
            }.exceptionOrNull()
            assertTrue(failure is WalletSecretMutationCoordinatorException)
            assertEquals(
                WalletMutationFailureReason.RECOVERY_REQUIRED,
                (failure as WalletSecretMutationCoordinatorException).reason
            )

            assertOriginalWallet(
                account = requireNotNull(dao.getMetaAccount(META_ID)),
                fixture = fixture
            )
            assertEthereumNotPublished(
                requireNotNull(dao.getMetaAccount(META_ID))
            )
            assertEquals(
                MALFORMED_STAGED_CIPHERTEXT,
                walletPreferences().getString(ETHEREUM_SECRET_KEY, null)
            )
            assertEquals(
                exactJournalCiphertext,
                walletPreferences().getString(
                    WalletSecretMutationJournalStore.JOURNAL_KEY,
                    null
                )
            )
            assertFalse(walletPreferences().contains(ETHEREUM_QUARANTINE_KEY))
            assertQuickCheck(database.openHelper.writableDatabase)
            closeDatabaseForRestart()
        }

        assertEquals(TARGET_DATABASE_VERSION, rawDatabaseVersion())
        assertRawWalletHasNoEthereumIdentity()
        assertFalse(walletPreferences().contains(ETHEREUM_QUARANTINE_KEY))
    }

    @Test
    fun attackerSizedTonUrlFailsDeletionWithoutMaterializingTheUrl() =
        runBlocking {
            assertSourceFixtureHashes()
            val fixture = createFixture(includeSecondWallet = false)
            val initialStack = newStorageStack(createFreshKey = true)
            migrationHelper.createDatabase(
                DATABASE_NAME,
                SOURCE_DATABASE_VERSION
            ).apply {
                insertVersion76Wallet(
                    metaId = META_ID,
                    substratePublicKey = fixture.substratePublicKey,
                    substrateAccountId = fixture.substrateAccountId,
                    name = WALLET_NAME,
                    isSelected = true,
                    position = WALLET_POSITION,
                    googleBackupAddress = GOOGLE_BACKUP_ADDRESS
                )
                close()
            }
            initialStack.encryptedPreferences.replaceEncryptedStringsDurably(
                valuesToPut = mapOf(
                    SUBSTRATE_SECRET_KEY to fixture.substratePlaintext
                ),
                keysToRemove = emptySet()
            )

            val stack = newStorageStack()
            val database = openProductionDatabase(stack)
            database.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO ton_connection(
                    metaId,
                    clientId,
                    name,
                    icon,
                    url,
                    source
                ) VALUES(?, ?, ?, ?, zeroblob(?), ?)
                """.trimIndent(),
                arrayOf(
                    META_ID,
                    "ab".repeat(16),
                    "Attacker-sized row",
                    "",
                    ATTACKER_TON_URL_BYTES,
                    "WEB"
                )
            )

            val failure = runCatching {
                newCoordinator(database, stack).delete(META_ID)
            }.exceptionOrNull()

            assertTrue(failure is WalletSecretMutationCoordinatorException)
            assertEquals(
                WalletMutationFailureReason.STATE_CONFLICT,
                (failure as WalletSecretMutationCoordinatorException).reason
            )
            assertNotNull(database.metaAccountDao().getMetaAccount(META_ID))
            assertFalse(
                stack.encryptedPreferences.hasKey(
                    WalletSecretMutationJournalStore.JOURNAL_KEY
                )
            )
            database.openHelper.writableDatabase.query(
                "SELECT length(url), typeof(url) FROM ton_connection " +
                    "WHERE metaId = ?",
                arrayOf(META_ID)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(ATTACKER_TON_URL_BYTES, cursor.getInt(0))
                assertEquals("blob", cursor.getString(1))
            }
        }

    private fun prepareInterruptedAddEvm(
        includeSecondWallet: Boolean = false
    ): AddEvmFixture {
        assertSourceFixtureHashes()
        val initialStack = newStorageStack(createFreshKey = true)
        val fixture = createFixture(includeSecondWallet)

        migrationHelper.createDatabase(DATABASE_NAME, SOURCE_DATABASE_VERSION).apply {
            insertVersion76Wallet(
                metaId = META_ID,
                substratePublicKey = fixture.substratePublicKey,
                substrateAccountId = fixture.substrateAccountId,
                name = WALLET_NAME,
                isSelected = true,
                position = WALLET_POSITION,
                googleBackupAddress = GOOGLE_BACKUP_ADDRESS
            )
            if (includeSecondWallet) {
                insertVersion76Wallet(
                    metaId = SECOND_META_ID,
                    substratePublicKey = checkNotNull(
                        fixture.secondSubstratePublicKey
                    ),
                    substrateAccountId = checkNotNull(
                        fixture.secondSubstrateAccountId
                    ),
                    name = SECOND_WALLET_NAME,
                    isSelected = false,
                    position = SECOND_WALLET_POSITION,
                    googleBackupAddress = null
                )
            }
            assertEquals(SOURCE_DATABASE_VERSION, version)
            close()
        }

        initialStack.encryptedPreferences.replaceEncryptedStringsDurably(
            valuesToPut = mapOf(
                SUBSTRATE_SECRET_KEY to fixture.substratePlaintext
            ),
            keysToRemove = emptySet()
        )
        WalletSecretMutationJournalStore(
            initialStack.encryptedPreferences
        ).stage(
            journal = fixture.journal,
            finalSecretPlaintexts = mapOf(
                ETHEREUM_SECRET_KEY to fixture.ethereumPlaintext
            )
        )

        assertEquals(
            fixture.substratePlaintext,
            initialStack.encryptedPreferences.getDecryptedString(
                SUBSTRATE_SECRET_KEY
            )
        )
        assertEquals(
            fixture.ethereumPlaintext,
            initialStack.encryptedPreferences.getDecryptedString(
                ETHEREUM_SECRET_KEY
            )
        )
        assertTrue(
            walletPreferences().getString(SUBSTRATE_SECRET_KEY, null)
                ?.startsWith(MODERN_CIPHERTEXT_PREFIX) == true
        )
        assertTrue(
            walletPreferences().getString(ETHEREUM_SECRET_KEY, null)
                ?.startsWith(MODERN_CIPHERTEXT_PREFIX) == true
        )
        assertTrue(
            walletPreferences().getString(
                WalletSecretMutationJournalStore.JOURNAL_KEY,
                null
            )?.startsWith(MODERN_CIPHERTEXT_PREFIX) == true
        )
        assertNoQuarantine(initialStack.encryptedPreferences)

        return fixture
    }

    private fun createFixture(includeSecondWallet: Boolean): AddEvmFixture {
        val substrateKeypair = EthereumKeypairFactory.createWithPrivateKey(
            deterministicPrivateKey(1)
        )
        val substrateAccountId = substrateKeypair.publicKey.substrateAccountId()
        val ethereumKeypair = EthereumKeypairFactory.createWithPrivateKey(
            deterministicPrivateKey(2)
        )
        val ethereumAddress =
            ethereumKeypair.publicKey.ethereumAddressFromPublicKey()
        val substratePlaintext = SubstrateSecrets(
            substrateKeyPair = substrateKeypair
        ).toHexString()
        val ethereumPlaintext = EthereumSecrets(
            seed = ethereumKeypair.privateKey,
            ethereumKeypair = ethereumKeypair
        ).toHexString()
        val beforeImage = PublicAfterImage(
            name = WALLET_NAME,
            substratePublicKeyHex = substrateKeypair.publicKey.toCanonicalHex(),
            substrateAccountIdHex = substrateAccountId.toCanonicalHex(),
            substrateCryptoType = SubstrateCryptoType.ECDSA,
            ethereumPublicKeyHex = null,
            ethereumAddressHex = null,
            tonPublicKeyHex = null,
            isSelected = true,
            position = WALLET_POSITION,
            isBackedUp = false,
            googleBackupAddress = GOOGLE_BACKUP_ADDRESS,
            initialized = true
        )
        val afterImage = beforeImage.copy(
            ethereumPublicKeyHex = ethereumKeypair.publicKey.toCanonicalHex(),
            ethereumAddressHex = ethereumAddress.toCanonicalHex()
        )
        val secondKeypair = if (includeSecondWallet) {
            EthereumKeypairFactory.createWithPrivateKey(
                deterministicPrivateKey(3)
            )
        } else {
            null
        }

        return AddEvmFixture(
            substratePrivateKey = substrateKeypair.privateKey,
            substratePublicKey = substrateKeypair.publicKey,
            substrateAccountId = substrateAccountId,
            ethereumPrivateKey = ethereumKeypair.privateKey,
            ethereumPublicKey = ethereumKeypair.publicKey,
            ethereumAddress = ethereumAddress,
            substratePlaintext = substratePlaintext,
            ethereumPlaintext = ethereumPlaintext,
            secondSubstratePublicKey = secondKeypair?.publicKey,
            secondSubstrateAccountId =
                secondKeypair?.publicKey?.substrateAccountId(),
            journal = Journal(
                operationId = OPERATION_ID,
                operation = Operation.ADD_EVM,
                metaId = META_ID,
                beforeImage = beforeImage,
                afterImage = afterImage,
                selectedMetaIdAfterDelete = null,
                chainAccountIdsHex = emptySet(),
                secretKeysToPut = setOf(ETHEREUM_SECRET_KEY),
                secretKeysToRemove = emptySet()
            )
        )
    }

    private fun newStorageStack(
        createFreshKey: Boolean = false
    ): ProductionStorageStack {
        val encryptionUtil = EncryptionUtil(context)
        if (createFreshKey) {
            encryptionUtil.getPrerenceAesKey()
        }
        val encryptedPreferences = EncryptedPreferencesImpl(
            preferences = PreferencesImpl(walletPreferences()),
            encryptionUtil = encryptionUtil
        )

        return ProductionStorageStack(
            encryptedPreferences = encryptedPreferences,
            substrateSecretStore = SubstrateSecretStore(encryptedPreferences),
            ethereumSecretStore = EthereumSecretStore(encryptedPreferences)
        )
    }

    private fun openProductionDatabase(
        stack: ProductionStorageStack
    ): AppDatabase {
        check(openedDatabase == null) {
            "The prior Room instance must be closed before a simulated restart"
        }
        resetAppDatabaseSingleton()

        return AppDatabase.get(
            context = context,
            storeV1 = SecretStoreV1Impl(stack.encryptedPreferences),
            storeV2 = SecretStoreV2(stack.encryptedPreferences),
            encryptedPreferences = stack.encryptedPreferences,
            substrateSecretStore = stack.substrateSecretStore,
            ethereumSecretStore = stack.ethereumSecretStore
        ).also { database ->
            openedDatabase = database
            database.openHelper.writableDatabase
        }
    }

    private fun newCoordinator(
        database: AppDatabase,
        stack: ProductionStorageStack
    ): WalletSecretMutationCoordinator {
        return WalletSecretMutationCoordinator(
            appDatabase = database,
            metaAccountDao = database.metaAccountDao(),
            journalStore = WalletSecretMutationJournalStore(
                stack.encryptedPreferences
            )
        )
    }

    private fun closeDatabaseForRestart() {
        val trackedDatabase = openedDatabase
        openedDatabase = null
        trackedDatabase?.close()

        val singletonField = appDatabaseSingletonField()
        val singletonDatabase = singletonField.get(null) as? AppDatabase
        if (singletonDatabase !== trackedDatabase) {
            singletonDatabase?.close()
        }
        singletonField.set(null, null)
    }

    private fun resetAppDatabaseSingleton() {
        val singletonField = appDatabaseSingletonField()
        check(singletonField.get(null) == null) {
            "The production Room singleton survived a simulated process restart"
        }
    }

    private fun appDatabaseSingletonField() =
        AppDatabase::class.java.getDeclaredField(APP_DATABASE_INSTANCE_FIELD).apply {
            isAccessible = true
        }

    private fun clearTestStorage() {
        closeDatabaseForRestart()
        context.deleteDatabase(DATABASE_NAME)
        walletPreferences().edit().clear().commit()
        keyPreferences().edit().clear().commit()

        val keyStore = KeyStore.getInstance(KEY_STORE_PROVIDER).apply {
            load(null)
        }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun SupportSQLiteDatabase.insertVersion76Wallet(
        metaId: Long,
        substratePublicKey: ByteArray,
        substrateAccountId: ByteArray,
        name: String,
        isSelected: Boolean,
        position: Int,
        googleBackupAddress: String?
    ) {
        execSQL(
            """
            INSERT INTO meta_accounts(
                id,
                substratePublicKey,
                substrateCryptoType,
                substrateAccountId,
                ethereumPublicKey,
                ethereumAddress,
                tonPublicKey,
                name,
                isSelected,
                position,
                isBackedUp,
                googleBackupAddress,
                initialized
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                metaId,
                substratePublicKey,
                CryptoType.ECDSA.name,
                substrateAccountId,
                null,
                null,
                null,
                name,
                if (isSelected) 1 else 0,
                position,
                0,
                googleBackupAddress,
                1
            )
        )
    }

    private suspend fun assertPostCommitState(
        dao: jp.co.soramitsu.coredb.dao.MetaAccountDao,
        fixture: AddEvmFixture
    ) {
        val target = requireNotNull(dao.getMetaAccount(META_ID))
        assertArrayEquals(fixture.substratePublicKey, target.substratePublicKey)
        assertArrayEquals(fixture.substrateAccountId, target.substrateAccountId)
        assertEquals(CryptoType.ECDSA, target.substrateCryptoType)
        assertArrayEquals(fixture.ethereumPublicKey, target.ethereumPublicKey)
        assertArrayEquals(fixture.ethereumAddress, target.ethereumAddress)
        assertEquals(POST_COMMIT_WALLET_NAME, target.name)
        assertFalse(target.isSelected)
        assertEquals(POST_COMMIT_POSITION, target.position)
        assertTrue(target.isBackedUp)
        assertEquals(POST_COMMIT_BACKUP_ADDRESS, target.googleBackupAddress)
        assertFalse(target.initialized)
        assertNull(target.tonPublicKey)

        val second = requireNotNull(dao.getMetaAccount(SECOND_META_ID))
        assertArrayEquals(
            fixture.secondSubstratePublicKey,
            second.substratePublicKey
        )
        assertArrayEquals(
            fixture.secondSubstrateAccountId,
            second.substrateAccountId
        )
        assertEquals(SECOND_WALLET_NAME, second.name)
        assertTrue(second.isSelected)
        assertEquals(SECOND_WALLET_POSITION, second.position)
        assertEquals(
            setOf(SECOND_META_ID),
            dao.getMetaAccounts()
                .filter(MetaAccountLocal::isSelected)
                .map(MetaAccountLocal::id)
                .toSet()
        )
    }

    private fun assertOriginalWallet(
        account: MetaAccountLocal,
        fixture: AddEvmFixture
    ) {
        assertEquals(META_ID, account.id)
        assertArrayEquals(fixture.substratePublicKey, account.substratePublicKey)
        assertArrayEquals(fixture.substrateAccountId, account.substrateAccountId)
        assertEquals(CryptoType.ECDSA, account.substrateCryptoType)
        assertEquals(WALLET_NAME, account.name)
        assertTrue(account.isSelected)
        assertEquals(WALLET_POSITION, account.position)
        assertFalse(account.isBackedUp)
        assertEquals(GOOGLE_BACKUP_ADDRESS, account.googleBackupAddress)
        assertTrue(account.initialized)
        assertNull(account.tonPublicKey)
    }

    private fun assertEthereumNotPublished(account: MetaAccountLocal) {
        assertNull(account.ethereumPublicKey)
        assertNull(account.ethereumAddress)
    }

    private fun assertEthereumPublished(
        account: MetaAccountLocal,
        fixture: AddEvmFixture
    ) {
        assertArrayEquals(fixture.ethereumPublicKey, account.ethereumPublicKey)
        assertArrayEquals(fixture.ethereumAddress, account.ethereumAddress)
    }

    private fun assertReadableSecrets(
        stack: ProductionStorageStack,
        account: MetaAccountLocal,
        fixture: AddEvmFixture
    ) {
        assertEquals(
            fixture.substratePlaintext,
            stack.encryptedPreferences.getDecryptedString(SUBSTRATE_SECRET_KEY)
        )
        assertEquals(
            fixture.ethereumPlaintext,
            stack.encryptedPreferences.getDecryptedString(ETHEREUM_SECRET_KEY)
        )

        val readableSubstrate = requireNotNull(
            stack.substrateSecretStore.get(
                metaId = META_ID,
                expectedPublicKey = account.substratePublicKey,
                expectedCryptoType = account.substrateCryptoType,
                expectedAccountId = account.substrateAccountId
            )
        )
        val readableSubstrateKeypair =
            readableSubstrate[SubstrateSecrets.SubstrateKeypair]
        assertArrayEquals(
            fixture.substratePrivateKey,
            readableSubstrateKeypair[KeyPairSchema.PrivateKey]
        )
        assertArrayEquals(
            fixture.substratePublicKey,
            readableSubstrateKeypair[KeyPairSchema.PublicKey]
        )

        val readableEthereum = requireNotNull(
            stack.ethereumSecretStore.get(
                metaId = META_ID,
                expectedPublicKey = account.ethereumPublicKey,
                expectedAddress = account.ethereumAddress
            )
        )
        val readableEthereumKeypair =
            readableEthereum[EthereumSecrets.EthereumKeypair]
        assertArrayEquals(
            fixture.ethereumPrivateKey,
            readableEthereumKeypair[KeyPairSchema.PrivateKey]
        )
        assertArrayEquals(
            fixture.ethereumPublicKey,
            readableEthereumKeypair[KeyPairSchema.PublicKey]
        )
    }

    private fun assertNoQuarantine(
        encryptedPreferences: EncryptedPreferences
    ) {
        assertFalse(encryptedPreferences.hasKey(SUBSTRATE_QUARANTINE_KEY))
        assertFalse(encryptedPreferences.hasKey(ETHEREUM_QUARANTINE_KEY))
    }

    private fun assertQuickCheck(database: SupportSQLiteDatabase) {
        database.query("PRAGMA quick_check(1)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(SQLITE_OK, cursor.getString(0))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun rawDatabaseVersion(): Int {
        return SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use(SQLiteDatabase::getVersion)
    }

    private fun assertRawWalletHasNoEthereumIdentity() {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { database ->
            database.rawQuery(
                """
                SELECT ethereumPublicKey, ethereumAddress
                FROM meta_accounts
                WHERE id = ?
                """.trimIndent(),
                arrayOf(META_ID.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
                assertFalse(cursor.moveToNext())
            }
        }
    }

    private fun walletStorageSnapshot(): Map<String, Any?> {
        return walletPreferences().all.entries
            .associate { (key, value) -> key to value }
            .toSortedMap()
    }

    private fun walletPreferences(): SharedPreferences {
        return context.getSharedPreferences(
            SHARED_PREFERENCES_FILE,
            Context.MODE_PRIVATE
        )
    }

    private fun keyPreferences(): SharedPreferences {
        return context.getSharedPreferences(
            KEY_ALIAS,
            Context.MODE_PRIVATE
        )
    }

    private fun assertSourceFixtureHashes() {
        assertEquals(
            VERSION_76_SCHEMA_SHA256,
            assetSha256(VERSION_76_SCHEMA_ASSET)
        )
        assertEquals(
            FROZEN_V1_JOURNAL_SHA256,
            assetSha256(FROZEN_V1_JOURNAL_ASSET)
        )
    }

    private fun assetSha256(assetName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(assetName).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toCanonicalHex()
    }

    private inline fun <reified T : Throwable> Throwable?.hasCause(): Boolean {
        var current = this
        while (current != null) {
            if (current is T) return true
            current = current.cause
        }
        return false
    }

    private fun deterministicPrivateKey(lastByte: Int): ByteArray {
        return ByteArray(PRIVATE_KEY_BYTES).apply {
            this[lastIndex] = lastByte.toByte()
        }
    }

    private fun ByteArray.toCanonicalHex(): String {
        return joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(HEX_RADIX).padStart(2, '0')
        }
    }

    private data class ProductionStorageStack(
        val encryptedPreferences: EncryptedPreferences,
        val substrateSecretStore: SubstrateSecretStore,
        val ethereumSecretStore: EthereumSecretStore
    )

    private data class AddEvmFixture(
        val substratePrivateKey: ByteArray,
        val substratePublicKey: ByteArray,
        val substrateAccountId: ByteArray,
        val ethereumPrivateKey: ByteArray,
        val ethereumPublicKey: ByteArray,
        val ethereumAddress: ByteArray,
        val substratePlaintext: String,
        val ethereumPlaintext: String,
        val secondSubstratePublicKey: ByteArray?,
        val secondSubstrateAccountId: ByteArray?,
        val journal: Journal
    )

    private companion object {
        const val DATABASE_NAME = "app.db"
        const val SOURCE_DATABASE_VERSION = 76
        const val TARGET_DATABASE_VERSION = 77
        const val META_ID = 4_242L
        const val SECOND_META_ID = 4_243L
        const val WALLET_NAME = "Interrupted ADD_EVM wallet"
        const val SECOND_WALLET_NAME = "Still selected wallet"
        const val POST_COMMIT_WALLET_NAME = "Renamed after Room commit"
        const val WALLET_POSITION = 7
        const val SECOND_WALLET_POSITION = 9
        const val POST_COMMIT_POSITION = 12
        const val GOOGLE_BACKUP_ADDRESS = "preserved@example.test"
        const val POST_COMMIT_BACKUP_ADDRESS = "newer@example.test"
        const val OPERATION_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val PRIVATE_KEY_BYTES = 32
        const val HEX_RADIX = 16
        const val SQLITE_OK = "ok"
        const val MALFORMED_RESTART_ATTEMPTS = 2
        const val ATTACKER_TON_URL_BYTES = 8_388_608

        const val SUBSTRATE_SECRET_KEY = "$META_ID:SUBSTRATE_SECRETS"
        const val ETHEREUM_SECRET_KEY = "$META_ID:ETHEREUM_SECRETS"
        val SUBSTRATE_QUARANTINE_KEY =
            WalletSecretQuarantine.keyFor(SUBSTRATE_SECRET_KEY)
        val ETHEREUM_QUARANTINE_KEY =
            WalletSecretQuarantine.keyFor(ETHEREUM_SECRET_KEY)

        const val KEY_STORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "key_alias"
        const val WRAPPED_AES_KEY_FIELD = "secret_key"
        const val APP_DATABASE_INSTANCE_FIELD = "instance"
        const val MODERN_CIPHERTEXT_PREFIX = "v2:"
        const val MALFORMED_WRAPPED_KEY = "%%%temporary-provider-failure%%%"
        const val MALFORMED_STAGED_CIPHERTEXT = "v2:%%%malformed-staged-secret%%%"

        const val VERSION_76_SCHEMA_ASSET =
            "jp.co.soramitsu.coredb.AppDatabase/76.json"
        const val VERSION_76_SCHEMA_SHA256 =
            "6468494a4d63fda723ea3e22528acaccb7b27b0c7a231a4eea93790ac4c1e71a"
        const val FROZEN_V1_JOURNAL_ASSET =
            "wallet_secret_mutation_journal_v1_delete.json"
        const val FROZEN_V1_JOURNAL_SHA256 =
            "e285f960e42fff890c0221e36d7e0bc6d3bb5c09b081dce596571c2ec398f87b"
    }
}

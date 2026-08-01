package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.Journal
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.Operation
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.PublicAfterImage
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString as toPlainHexString
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic

class WalletSecretIntegrityMigrationTest {

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
    fun deleteTestDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
        createdDatabases.clear()
    }

    @Test
    fun version76ValidSr25519EthereumAndTonSecretsSurviveTwoProductionLaunches() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        val ethereum = ethereumFixture()
        val ton = tonFixture(3)
        val identity = FixtureIdentity(
            substratePublicKey = substrate.keypair.publicKey,
            substrateCryptoType = CryptoType.SR25519.name,
            substrateAccountId = substrate.keypair.publicKey.substrateAccountId(),
            ethereumPublicKey = ethereum.keypair.publicKey,
            ethereumAddress = ethereum.keypair.publicKey.ethereumAddressFromPublicKey(),
            tonPublicKey = ton.publicKey
        )
        createFixtureDatabase(VALID_ALL_DATABASE, 76, identity)
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = substrate.encoded
            values[ethereumSecretKey()] = ethereum.encoded
            values[tonSecretKey()] = ton.encoded
        }

        assertEquals(77, openProductionDatabase(VALID_ALL_DATABASE, TestPreferences(backing)))
        val afterFirstLaunch = backing.values.toMap()
        assertEquals(77, openProductionDatabase(VALID_ALL_DATABASE, TestPreferences(backing)))

        assertEquals(afterFirstLaunch, backing.values)
        assertEquals(substrate.encoded, backing.values[substrateSecretKey()])
        assertEquals(ethereum.encoded, backing.values[ethereumSecretKey()])
        assertEquals(ton.encoded, backing.values[tonSecretKey()])
    }

    @Test
    fun version74FixtureSanitizesOnlyUnboundPathsAndPreservesDirectImportSigning() {
        val substrateKeypair = directSubstrateKeypair(EncryptionType.ED25519, 11)
        val ethereumKeypair = directEthereumKeypair(12)
        val substrateEncoded = SubstrateSecrets(
            substrateKeyPair = substrateKeypair,
            substrateDerivationPath = "//unbound"
        ).toHexString()
        val ethereumEncoded = EthereumSecrets(
            seed = ethereumKeypair.privateKey,
            ethereumKeypair = ethereumKeypair,
            ethereumDerivationPath = "//44//60//0/0/99"
        ).toHexString()
        createFixtureDatabase(
            VERSION_74_SANITIZE_DATABASE,
            74,
            FixtureIdentity(
                substratePublicKey = substrateKeypair.publicKey,
                substrateCryptoType = CryptoType.ED25519.name,
                substrateAccountId = substrateKeypair.publicKey.substrateAccountId(),
                ethereumPublicKey = ethereumKeypair.publicKey,
                ethereumAddress = ethereumKeypair.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = substrateEncoded
            values[ethereumSecretKey()] = ethereumEncoded
        }

        assertEquals(
            77,
            openProductionDatabase(VERSION_74_SANITIZE_DATABASE, TestPreferences(backing))
        )

        val substrate = SubstrateSecrets.read(backing.values.getValue(substrateSecretKey()))
        val substrateKeypairAfter = substrate[SubstrateSecrets.SubstrateKeypair]
        assertNull(substrate[SubstrateSecrets.SubstrateDerivationPath])
        assertArrayEquals(
            substrateKeypair.privateKey,
            substrateKeypairAfter[KeyPairSchema.PrivateKey]
        )
        assertArrayEquals(
            substrateKeypair.publicKey,
            substrateKeypairAfter[KeyPairSchema.PublicKey]
        )

        val ethereum = EthereumSecrets.read(backing.values.getValue(ethereumSecretKey()))
        val ethereumKeypairAfter = ethereum[EthereumSecrets.EthereumKeypair]
        assertNull(ethereum[EthereumSecrets.EthereumDerivationPath])
        assertArrayEquals(
            ethereumKeypair.privateKey,
            ethereumKeypairAfter[KeyPairSchema.PrivateKey]
        )
        assertArrayEquals(
            ethereumKeypair.publicKey,
            ethereumKeypairAfter[KeyPairSchema.PublicKey]
        )
    }

    @Test
    fun entropyNullSubstrateSeedKeepsItsPathWhileEthereumPathIsSanitized() {
        val substrateKeypair = directSubstrateKeypair(EncryptionType.ECDSA, 13)
        val ethereumKeypair = directEthereumKeypair(14)
        val substratePath = "//hard"
        val substrateEncoded = SubstrateSecrets(
            substrateKeyPair = substrateKeypair,
            seed = privateKey(13),
            substrateDerivationPath = substratePath
        ).toHexString()
        val ethereumEncoded = EthereumSecrets(
            seed = ethereumKeypair.privateKey,
            ethereumKeypair = ethereumKeypair,
            ethereumDerivationPath = "//44//60//0/0/17"
        ).toHexString()
        createFixtureDatabase(
            ENTROPY_NULL_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrateKeypair.publicKey,
                substrateCryptoType = CryptoType.ECDSA.name,
                substrateAccountId = substrateKeypair.publicKey.substrateAccountId(),
                ethereumPublicKey = ethereumKeypair.publicKey,
                ethereumAddress = ethereumKeypair.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = substrateEncoded
            values[ethereumSecretKey()] = ethereumEncoded
        }

        assertEquals(77, openProductionDatabase(ENTROPY_NULL_DATABASE, TestPreferences(backing)))

        val substrate = SubstrateSecrets.read(backing.values.getValue(substrateSecretKey()))
        assertEquals(substratePath, substrate[SubstrateSecrets.SubstrateDerivationPath])
        assertArrayEquals(privateKey(13), substrate[SubstrateSecrets.Seed])
        val ethereum = EthereumSecrets.read(backing.values.getValue(ethereumSecretKey()))
        assertNull(ethereum[EthereumSecrets.Entropy])
        assertNull(ethereum[EthereumSecrets.EthereumDerivationPath])
        assertArrayEquals(
            ethereumKeypair.privateKey,
            ethereum[EthereumSecrets.Seed]
        )
    }

    @Test
    fun alreadyCommittedVersion76LegacySecretIsFinishedIdempotently() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        val ethereum = ethereumFixture()
        createFixtureDatabase(
            VERSION_76_LEGACY_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId(),
                ethereumPublicKey = ethereum.keypair.publicKey,
                ethereumAddress = ethereum.keypair.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val legacy = legacySecrets(
            substrateKeypair = substrate.keypair,
            ethereumKeypair = ethereum.keypair
        )
        val backing = SecretBacking().apply {
            values[legacySecretKey()] = legacy
        }

        assertEquals(
            77,
            openProductionDatabase(VERSION_76_LEGACY_DATABASE, TestPreferences(backing))
        )
        assertFalse(legacySecretKey() in backing.values)
        assertTrue(substrateSecretKey() in backing.values)
        assertTrue(ethereumSecretKey() in backing.values)
        val afterFirstLaunch = backing.values.toMap()

        assertEquals(
            77,
            openProductionDatabase(VERSION_76_LEGACY_DATABASE, TestPreferences(backing))
        )
        assertEquals(afterFirstLaunch, backing.values)
    }

    @Test
    fun version76LegacyEntropyNullSeedBackedSecretsPreserveSigningAndBoundSubstratePath() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        val ethereum = ethereumFixture()
        createFixtureDatabase(
            VERSION_76_LEGACY_ENTROPY_NULL_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId(),
                ethereumPublicKey = ethereum.keypair.publicKey,
                ethereumAddress = ethereum.keypair.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val substrateSeed =
            SubstrateSecrets.read(substrate.encoded)[SubstrateSecrets.Seed]
        val backing = SecretBacking().apply {
            values[legacySecretKey()] = legacySecrets(
                substrateKeypair = substrate.keypair,
                ethereumKeypair = ethereum.keypair,
                entropy = null,
                substrateSeed = substrateSeed
            )
        }

        assertEquals(
            77,
            openProductionDatabase(
                VERSION_76_LEGACY_ENTROPY_NULL_DATABASE,
                TestPreferences(backing)
            )
        )

        assertFalse(legacySecretKey() in backing.values)
        assertFalse(quarantineKey(legacySecretKey()) in backing.values)
        val substrateAfter =
            SubstrateSecrets.read(backing.values.getValue(substrateSecretKey()))
        val substrateKeypairAfter =
            substrateAfter[SubstrateSecrets.SubstrateKeypair]
        assertNull(substrateAfter[SubstrateSecrets.Entropy])
        assertArrayEquals(substrateSeed, substrateAfter[SubstrateSecrets.Seed])
        assertEquals(
            SUBSTRATE_PATH,
            substrateAfter[SubstrateSecrets.SubstrateDerivationPath]
        )
        assertArrayEquals(
            substrate.keypair.publicKey,
            substrateKeypairAfter[KeyPairSchema.PublicKey]
        )
        assertArrayEquals(
            substrate.keypair.privateKey,
            substrateKeypairAfter[KeyPairSchema.PrivateKey]
        )

        val ethereumAfter =
            EthereumSecrets.read(backing.values.getValue(ethereumSecretKey()))
        val ethereumKeypairAfter =
            ethereumAfter[EthereumSecrets.EthereumKeypair]
        assertNull(ethereumAfter[EthereumSecrets.Entropy])
        assertNull(ethereumAfter[EthereumSecrets.EthereumDerivationPath])
        assertArrayEquals(
            ethereum.keypair.privateKey,
            ethereumAfter[EthereumSecrets.Seed]
        )
        assertArrayEquals(
            ethereum.keypair.publicKey,
            ethereumKeypairAfter[KeyPairSchema.PublicKey]
        )
        assertArrayEquals(
            ethereum.keypair.privateKey,
            ethereumKeypairAfter[KeyPairSchema.PrivateKey]
        )
    }

    @Test
    fun version76LegacyKeypairOnlySecretsSanitizeBothUnboundPathsAndRemainUsable() {
        val substrateKeypair = directSubstrateKeypair(EncryptionType.SR25519, 61)
        val ethereumKeypair = directEthereumKeypair(62)
        createFixtureDatabase(
            VERSION_76_LEGACY_KEYPAIR_ONLY_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrateKeypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrateKeypair.publicKey.substrateAccountId(),
                ethereumPublicKey = ethereumKeypair.publicKey,
                ethereumAddress = ethereumKeypair.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val backing = SecretBacking().apply {
            values[legacySecretKey()] = legacySecrets(
                substrateKeypair = substrateKeypair,
                ethereumKeypair = ethereumKeypair,
                entropy = null,
                substrateSeed = null,
                substratePath = "//unbound-substrate",
                ethereumPath = "//44//60//0/0/404"
            )
        }

        assertEquals(
            77,
            openProductionDatabase(
                VERSION_76_LEGACY_KEYPAIR_ONLY_DATABASE,
                TestPreferences(backing)
            )
        )

        assertFalse(legacySecretKey() in backing.values)
        assertFalse(quarantineKey(legacySecretKey()) in backing.values)
        val substrateAfter =
            SubstrateSecrets.read(backing.values.getValue(substrateSecretKey()))
        val substrateKeypairAfter =
            substrateAfter[SubstrateSecrets.SubstrateKeypair]
        assertNull(substrateAfter[SubstrateSecrets.Entropy])
        assertNull(substrateAfter[SubstrateSecrets.Seed])
        assertNull(substrateAfter[SubstrateSecrets.SubstrateDerivationPath])
        assertArrayEquals(
            substrateKeypair.publicKey,
            substrateKeypairAfter[KeyPairSchema.PublicKey]
        )
        assertArrayEquals(
            substrateKeypair.privateKey,
            substrateKeypairAfter[KeyPairSchema.PrivateKey]
        )

        val ethereumAfter =
            EthereumSecrets.read(backing.values.getValue(ethereumSecretKey()))
        val ethereumKeypairAfter =
            ethereumAfter[EthereumSecrets.EthereumKeypair]
        assertNull(ethereumAfter[EthereumSecrets.Entropy])
        assertNull(ethereumAfter[EthereumSecrets.EthereumDerivationPath])
        assertArrayEquals(
            ethereumKeypair.privateKey,
            ethereumAfter[EthereumSecrets.Seed]
        )
        assertArrayEquals(
            ethereumKeypair.publicKey,
            ethereumKeypairAfter[KeyPairSchema.PublicKey]
        )
        assertArrayEquals(
            ethereumKeypair.privateKey,
            ethereumKeypairAfter[KeyPairSchema.PrivateKey]
        )
    }

    @Test
    fun committedLegacyReplacementWithSqlRollbackRetriesFromExactV3Secrets() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        val ethereum = ethereumFixture()
        createFixtureDatabase(
            LEGACY_REPLACEMENT_RETRY_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId(),
                ethereumPublicKey = ethereum.keypair.publicKey,
                ethereumAddress = ethereum.keypair.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val backing = SecretBacking().apply {
            values[legacySecretKey()] = legacySecrets(
                substrateKeypair = substrate.keypair,
                ethereumKeypair = ethereum.keypair
            )
        }
        val failedPreferences = TestPreferences(backing).apply {
            durableFailure = DurableFailure(
                point = DurableFailurePoint.AFTER_REPLACE,
                key = legacySecretKey()
            )
        }

        assertThrows(RuntimeException::class.java) {
            openProductionDatabase(LEGACY_REPLACEMENT_RETRY_DATABASE, failedPreferences)
        }

        assertEquals(76, rawDatabaseVersion(LEGACY_REPLACEMENT_RETRY_DATABASE))
        assertFalse(legacySecretKey() in backing.values)
        assertTrue(substrateSecretKey() in backing.values)
        assertTrue(ethereumSecretKey() in backing.values)
        val committedV3 = backing.values.toMap()

        assertEquals(
            77,
            openProductionDatabase(
                LEGACY_REPLACEMENT_RETRY_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(committedV3, backing.values)
        assertEquals(
            77,
            openProductionDatabase(
                LEGACY_REPLACEMENT_RETRY_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(committedV3, backing.values)
    }

    @Test
    fun concurrentLegacySourceMutationCannotPublishStaleSeparatedSecrets() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        val ethereum = ethereumFixture()
        createFixtureDatabase(
            LEGACY_SOURCE_RACE_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId(),
                ethereumPublicKey = ethereum.keypair.publicKey,
                ethereumAddress = ethereum.keypair.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val originalLegacy = legacySecrets(
            substrateKeypair = substrate.keypair,
            ethereumKeypair = ethereum.keypair
        )
        val concurrentLegacy = legacySecrets(
            substrateKeypair = substrate.keypair,
            ethereumKeypair = ethereum.keypair,
            entropy = null,
            substrateSeed = null,
            substratePath = "//concurrent-unbound",
            ethereumPath = "//44//60//0/0/777"
        )
        val backing = SecretBacking().apply {
            values[legacySecretKey()] = originalLegacy
        }
        val racingPreferences = TestPreferences(backing).apply {
            beforeNextSnapshotBoundReplace = {
                backing.values[legacySecretKey()] = concurrentLegacy
            }
        }

        assertIntegrityMigrationRejectsConcurrentMutation(
            databaseName = LEGACY_SOURCE_RACE_DATABASE,
            preferences = racingPreferences
        )

        assertEquals(76, rawDatabaseVersion(LEGACY_SOURCE_RACE_DATABASE))
        assertEquals(concurrentLegacy, backing.values[legacySecretKey()])
        assertFalse(substrateSecretKey() in backing.values)
        assertFalse(ethereumSecretKey() in backing.values)
        assertEquals(1, racingPreferences.snapshotBoundReplaceCount)
        assertEquals(0, racingPreferences.durableWriteCount)

        assertEquals(
            77,
            openProductionDatabase(
                LEGACY_SOURCE_RACE_DATABASE,
                TestPreferences(backing)
            )
        )
        assertFalse(legacySecretKey() in backing.values)
        assertTrue(substrateSecretKey() in backing.values)
        assertTrue(ethereumSecretKey() in backing.values)
        val afterRetry = backing.values.toMap()
        assertEquals(
            77,
            openProductionDatabase(
                LEGACY_SOURCE_RACE_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(afterRetry, backing.values)
    }

    @Test
    fun preexistingConflictingLegacyTargetFailsBeforeAnyCasOrMutation() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        val ethereum = ethereumFixture()
        createFixtureDatabase(
            LEGACY_TARGET_CONFLICT_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId(),
                ethereumPublicKey = ethereum.keypair.publicKey,
                ethereumAddress = ethereum.keypair.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val conflict = "concurrently-owned-substrate-target"
        val backing = SecretBacking().apply {
            values[legacySecretKey()] = legacySecrets(
                substrateKeypair = substrate.keypair,
                ethereumKeypair = ethereum.keypair
            )
            values[substrateSecretKey()] = conflict
        }
        val exactBefore = backing.values.toMap()
        val preferences = TestPreferences(backing)

        assertThrows(WalletSecretConcurrentMutationException::class.java) {
            openProductionDatabase(
                LEGACY_TARGET_CONFLICT_DATABASE,
                preferences
            )
        }

        assertEquals(76, rawDatabaseVersion(LEGACY_TARGET_CONFLICT_DATABASE))
        assertEquals(exactBefore, backing.values)
        assertEquals(0, preferences.snapshotBoundReplaceCount)
        assertEquals(0, preferences.durableWriteCount)

        backing.values.remove(substrateSecretKey())
        assertEquals(
            77,
            openProductionDatabase(
                LEGACY_TARGET_CONFLICT_DATABASE,
                TestPreferences(backing)
            )
        )
        assertFalse(legacySecretKey() in backing.values)
    }

    @Test
    fun concurrentLegacyTargetCreationCannotBeOverwritten() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        val ethereum = ethereumFixture()
        createFixtureDatabase(
            LEGACY_TARGET_RACE_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId(),
                ethereumPublicKey = ethereum.keypair.publicKey,
                ethereumAddress = ethereum.keypair.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val originalLegacy = legacySecrets(
            substrateKeypair = substrate.keypair,
            ethereumKeypair = ethereum.keypair
        )
        val conflict = "concurrently-created-ethereum-target"
        val backing = SecretBacking().apply {
            values[legacySecretKey()] = originalLegacy
        }
        val racingPreferences = TestPreferences(backing).apply {
            beforeNextSnapshotBoundReplace = {
                backing.values[ethereumSecretKey()] = conflict
            }
        }

        assertThrows(WalletSecretConcurrentMutationException::class.java) {
            openProductionDatabase(
                LEGACY_TARGET_RACE_DATABASE,
                racingPreferences
            )
        }

        assertEquals(76, rawDatabaseVersion(LEGACY_TARGET_RACE_DATABASE))
        assertEquals(originalLegacy, backing.values[legacySecretKey()])
        assertEquals(conflict, backing.values[ethereumSecretKey()])
        assertFalse(substrateSecretKey() in backing.values)
        assertEquals(1, racingPreferences.snapshotBoundReplaceCount)
        assertEquals(0, racingPreferences.durableWriteCount)

        backing.values.remove(ethereumSecretKey())
        assertEquals(
            77,
            openProductionDatabase(
                LEGACY_TARGET_RACE_DATABASE,
                TestPreferences(backing)
            )
        )
        assertFalse(legacySecretKey() in backing.values)
        assertTrue(substrateSecretKey() in backing.values)
        assertTrue(ethereumSecretKey() in backing.values)
    }

    @Test
    fun exactLegacyTargetIsPreservedWhileMissingSiblingPublishesAtomically() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        val ethereum = ethereumFixture()
        createFixtureDatabase(
            LEGACY_PARTIAL_TARGET_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId(),
                ethereumPublicKey = ethereum.keypair.publicKey,
                ethereumAddress = ethereum.keypair.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val backing = SecretBacking().apply {
            values[legacySecretKey()] = legacySecrets(
                substrateKeypair = substrate.keypair,
                ethereumKeypair = ethereum.keypair
            )
            values[substrateSecretKey()] = substrate.encoded
        }
        val preferences = TestPreferences(backing)

        assertEquals(
            77,
            openProductionDatabase(
                LEGACY_PARTIAL_TARGET_DATABASE,
                preferences
            )
        )

        assertFalse(legacySecretKey() in backing.values)
        assertEquals(substrate.encoded, backing.values[substrateSecretKey()])
        assertEquals(ethereum.encoded, backing.values[ethereumSecretKey()])
        assertEquals(1, preferences.durableWriteCount)
        val afterMigration = backing.values.toMap()
        assertEquals(
            77,
            openProductionDatabase(
                LEGACY_PARTIAL_TARGET_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(afterMigration, backing.values)
    }

    @Test
    fun canonicalRootSecretStillUsesCasAndRejectsConcurrentReplacement() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        val concurrentCanonical = SubstrateSecrets(
            substrateKeyPair = substrate.keypair
        ).toHexString()
        assertFalse(substrate.encoded == concurrentCanonical)
        createFixtureDatabase(
            CANONICAL_ROOT_RACE_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId()
            )
        )
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = substrate.encoded
        }
        val racingPreferences = TestPreferences(backing).apply {
            beforeNextSnapshotBoundReplace = {
                backing.values[substrateSecretKey()] = concurrentCanonical
            }
        }

        assertIntegrityMigrationRejectsConcurrentMutation(
            databaseName = CANONICAL_ROOT_RACE_DATABASE,
            preferences = racingPreferences
        )

        assertEquals(76, rawDatabaseVersion(CANONICAL_ROOT_RACE_DATABASE))
        assertEquals(concurrentCanonical, backing.values[substrateSecretKey()])
        assertEquals(1, racingPreferences.snapshotBoundReplaceCount)
        assertEquals(0, racingPreferences.durableWriteCount)

        val retryPreferences = TestPreferences(backing)
        assertEquals(
            77,
            openProductionDatabase(
                CANONICAL_ROOT_RACE_DATABASE,
                retryPreferences
            )
        )
        assertEquals(concurrentCanonical, backing.values[substrateSecretKey()])
        assertEquals(1, retryPreferences.snapshotBoundReplaceCount)
        assertEquals(0, retryPreferences.durableWriteCount)
    }

    @Test
    fun rootSanitizationRejectsStaleSnapshotAndCleanRetrySanitizesLatestValue() {
        val keypair = directSubstrateKeypair(EncryptionType.ED25519, 71)
        val original = SubstrateSecrets(
            substrateKeyPair = keypair,
            substrateDerivationPath = "//original-unbound"
        ).toHexString()
        val concurrent = SubstrateSecrets(
            substrateKeyPair = keypair,
            substrateDerivationPath = "//concurrent-unbound"
        ).toHexString()
        createFixtureDatabase(
            ROOT_SANITIZE_RACE_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = keypair.publicKey,
                substrateCryptoType = CryptoType.ED25519.name,
                substrateAccountId = keypair.publicKey.substrateAccountId()
            )
        )
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = original
        }
        val racingPreferences = TestPreferences(backing).apply {
            beforeNextSnapshotBoundReplace = {
                backing.values[substrateSecretKey()] = concurrent
            }
        }

        assertIntegrityMigrationRejectsConcurrentMutation(
            databaseName = ROOT_SANITIZE_RACE_DATABASE,
            preferences = racingPreferences
        )

        assertEquals(76, rawDatabaseVersion(ROOT_SANITIZE_RACE_DATABASE))
        assertEquals(concurrent, backing.values[substrateSecretKey()])
        assertEquals(1, racingPreferences.snapshotBoundReplaceCount)
        assertEquals(0, racingPreferences.durableWriteCount)

        val retryPreferences = TestPreferences(backing)
        assertEquals(
            77,
            openProductionDatabase(
                ROOT_SANITIZE_RACE_DATABASE,
                retryPreferences
            )
        )
        val sanitized = SubstrateSecrets.read(
            backing.values.getValue(substrateSecretKey())
        )
        assertNull(sanitized[SubstrateSecrets.SubstrateDerivationPath])
        assertArrayEquals(
            keypair.privateKey,
            sanitized[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PrivateKey]
        )
        assertEquals(1, retryPreferences.durableWriteCount)
        val afterRetry = backing.values.toMap()
        assertEquals(
            77,
            openProductionDatabase(
                ROOT_SANITIZE_RACE_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(afterRetry, backing.values)
    }

    @Test
    fun chainSanitizationRejectsStaleSnapshotAndCleanRetrySanitizesLatestValue() {
        val keypair = directSubstrateKeypair(EncryptionType.ECDSA, 72)
        val accountId = keypair.publicKey.substrateAccountId()
        val activeKey = chainSecretKey(accountId)
        val original = ChainAccountSecrets(
            keyPair = keypair,
            derivationPath = "//original-chain-unbound"
        ).toHexString()
        val concurrent = ChainAccountSecrets(
            keyPair = keypair,
            derivationPath = "//concurrent-chain-unbound"
        ).toHexString()
        createFixtureDatabase(
            CHAIN_SANITIZE_RACE_DATABASE,
            76,
            FixtureIdentity()
        )
        addChainAccount(
            databaseName = CHAIN_SANITIZE_RACE_DATABASE,
            publicKey = keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        val backing = SecretBacking().apply {
            values[activeKey] = original
        }
        val racingPreferences = TestPreferences(backing).apply {
            beforeNextSnapshotBoundReplace = {
                backing.values[activeKey] = concurrent
            }
        }

        assertIntegrityMigrationRejectsConcurrentMutation(
            databaseName = CHAIN_SANITIZE_RACE_DATABASE,
            preferences = racingPreferences
        )

        assertEquals(76, rawDatabaseVersion(CHAIN_SANITIZE_RACE_DATABASE))
        assertEquals(concurrent, backing.values[activeKey])
        assertEquals(1, racingPreferences.snapshotBoundReplaceCount)
        assertEquals(0, racingPreferences.durableWriteCount)

        val retryPreferences = TestPreferences(backing)
        assertEquals(
            77,
            openProductionDatabase(
                CHAIN_SANITIZE_RACE_DATABASE,
                retryPreferences
            )
        )
        val sanitized = ChainAccountSecrets.read(
            backing.values.getValue(activeKey)
        )
        assertNull(sanitized[ChainAccountSecrets.DerivationPath])
        assertArrayEquals(
            keypair.privateKey,
            sanitized[ChainAccountSecrets.Keypair][KeyPairSchema.PrivateKey]
        )
        assertEquals(1, retryPreferences.durableWriteCount)
    }

    @Test
    fun exactMarkerCreationRaceRollsBackThenValidMarkerMakesRetryIdempotent() {
        val ethereum = ethereumFixture()
        createFixtureDatabase(
            MARKER_RACE_DATABASE,
            76,
            FixtureIdentity(ethereumPublicKey = ethereum.keypair.publicKey)
        )
        val markerKey = publicIdentityMarker(ethereumSecretKey())
        val backing = SecretBacking().apply {
            values[ethereumSecretKey()] = ethereum.encoded
        }
        val racingPreferences = TestPreferences(backing).apply {
            beforeNextSnapshotBoundReplace = {
                backing.values[markerKey] =
                    WalletPublicIdentityRecovery.MARKER_VALUE
            }
        }

        assertIntegrityMigrationRejectsConcurrentMutation(
            databaseName = MARKER_RACE_DATABASE,
            preferences = racingPreferences
        )

        assertEquals(76, rawDatabaseVersion(MARKER_RACE_DATABASE))
        assertEquals(ethereum.encoded, backing.values[ethereumSecretKey()])
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            backing.values[markerKey]
        )
        assertEquals(1, racingPreferences.snapshotBoundReplaceCount)
        assertEquals(0, racingPreferences.durableWriteCount)

        val retryPreferences = TestPreferences(backing)
        assertEquals(
            77,
            openProductionDatabase(
                MARKER_RACE_DATABASE,
                retryPreferences
            )
        )
        assertEquals(0, retryPreferences.durableWriteCount)
        val afterRetry = backing.values.toMap()
        assertEquals(
            77,
            openProductionDatabase(
                MARKER_RACE_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(afterRetry, backing.values)
    }

    @Test
    fun sr25519PrivateMismatchQuarantinesOnlyExactSubstrateCiphertext() {
        assertSubstratePrivateMismatch(
            databaseName = SR25519_MISMATCH_DATABASE,
            encryptionType = EncryptionType.SR25519,
            cryptoType = CryptoType.SR25519
        )
    }

    @Test
    fun ed25519PrivateMismatchQuarantinesOnlyExactSubstrateCiphertext() {
        assertSubstratePrivateMismatch(
            databaseName = ED25519_MISMATCH_DATABASE,
            encryptionType = EncryptionType.ED25519,
            cryptoType = CryptoType.ED25519
        )
    }

    @Test
    fun ecdsaPrivateMismatchQuarantinesOnlyExactSubstrateCiphertext() {
        assertSubstratePrivateMismatch(
            databaseName = ECDSA_MISMATCH_DATABASE,
            encryptionType = EncryptionType.ECDSA,
            cryptoType = CryptoType.ECDSA
        )
    }

    @Test
    fun ethereumPrivateMismatchQuarantinesOnlyExactEthereumCiphertext() {
        val valid = directEthereumKeypair(21)
        val other = directEthereumKeypair(22)
        val forged = Keypair(
            publicKey = valid.publicKey,
            privateKey = other.privateKey
        )
        val encoded = EthereumSecrets(
            seed = other.privateKey,
            ethereumKeypair = forged
        ).toHexString()
        createFixtureDatabase(
            ETHEREUM_MISMATCH_DATABASE,
            76,
            FixtureIdentity(
                ethereumPublicKey = valid.publicKey,
                ethereumAddress = valid.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val backing = SecretBacking().apply {
            values[ethereumSecretKey()] = encoded
        }

        assertEquals(
            77,
            openProductionDatabase(ETHEREUM_MISMATCH_DATABASE, TestPreferences(backing))
        )

        assertFalse(ethereumSecretKey() in backing.values)
        assertEquals(encoded, backing.values[quarantineKey(ethereumSecretKey())])
    }

    @Test
    fun tonPrivateMismatchQuarantinesOnlyExactTonCiphertext() {
        val valid = tonFixture(31)
        val other = tonFixture(32)
        val otherSecrets = TonSecrets.read(other.encoded)
        val forged = TonSecrets(
            seed = otherSecrets[TonSecrets.Seed],
            tonKeypair = Keypair(
                publicKey = valid.publicKey,
                privateKey = otherSecrets[TonSecrets.PrivateKey]
            )
        ).toHexString()
        createFixtureDatabase(
            TON_MISMATCH_DATABASE,
            76,
            FixtureIdentity(tonPublicKey = valid.publicKey)
        )
        val backing = SecretBacking().apply {
            values[tonSecretKey()] = forged
        }

        assertEquals(77, openProductionDatabase(TON_MISMATCH_DATABASE, TestPreferences(backing)))

        assertFalse(tonSecretKey() in backing.values)
        assertEquals(forged, backing.values[quarantineKey(tonSecretKey())])
    }

    @Test
    fun malformedSubstrateDoesNotTouchValidEthereumCiphertext() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        val ethereum = ethereumFixture()
        createFixtureDatabase(
            ISOLATED_QUARANTINE_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId(),
                ethereumPublicKey = ethereum.keypair.publicKey,
                ethereumAddress = ethereum.keypair.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val malformed = "not-scale-hex-\u0000-\uD83D\uDD10"
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = malformed
            values[ethereumSecretKey()] = ethereum.encoded
        }

        assertEquals(
            77,
            openProductionDatabase(ISOLATED_QUARANTINE_DATABASE, TestPreferences(backing))
        )

        assertEquals(malformed, backing.values[quarantineKey(substrateSecretKey())])
        assertEquals(ethereum.encoded, backing.values[ethereumSecretKey()])
        assertFalse(quarantineKey(ethereumSecretKey()) in backing.values)
    }

    @Test
    fun exactExistingQuarantineIsAcceptedAsRecoveryEvidenceAcrossTwoLaunches() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        createFixtureDatabase(
            EXISTING_QUARANTINE_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId()
            )
        )
        val exactCiphertext = "v2:exact-unreadable-ciphertext"
        val backing = SecretBacking().apply {
            values[quarantineKey(substrateSecretKey())] = exactCiphertext
        }

        assertEquals(
            77,
            openProductionDatabase(EXISTING_QUARANTINE_DATABASE, TestPreferences(backing))
        )
        assertEquals(
            77,
            openProductionDatabase(EXISTING_QUARANTINE_DATABASE, TestPreferences(backing))
        )
        assertEquals(
            exactCiphertext,
            backing.values[quarantineKey(substrateSecretKey())]
        )
    }

    @Test
    fun genericReadFailureRollsBackSqlAndNeverQuarantinesThenNewProcessRetries() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        createFixtureDatabase(
            READ_FAILURE_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId()
            )
        )
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = substrate.encoded
        }
        val failedPreferences = TestPreferences(backing).apply {
            genericReadFailureKey = substrateSecretKey()
        }

        assertThrows(RuntimeException::class.java) {
            openProductionDatabase(READ_FAILURE_DATABASE, failedPreferences)
        }

        assertEquals(76, rawDatabaseVersion(READ_FAILURE_DATABASE))
        assertEquals(substrate.encoded, backing.values[substrateSecretKey()])
        assertFalse(quarantineKey(substrateSecretKey()) in backing.values)

        assertEquals(
            77,
            openProductionDatabase(READ_FAILURE_DATABASE, TestPreferences(backing))
        )
        assertEquals(substrate.encoded, backing.values[substrateSecretKey()])
    }

    @Test
    fun globalSecureStorageFailureRollsBackWithoutQuarantine() {
        val ethereum = ethereumFixture()
        createFixtureDatabase(
            SECURE_STORAGE_FAILURE_DATABASE,
            76,
            FixtureIdentity(
                ethereumPublicKey = ethereum.keypair.publicKey,
                ethereumAddress = ethereum.keypair.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val backing = SecretBacking().apply {
            values[ethereumSecretKey()] = ethereum.encoded
        }
        val failedPreferences = TestPreferences(backing).apply {
            secureReadFailureKey = ethereumSecretKey()
        }

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            openProductionDatabase(SECURE_STORAGE_FAILURE_DATABASE, failedPreferences)
        }

        assertEquals(76, rawDatabaseVersion(SECURE_STORAGE_FAILURE_DATABASE))
        assertEquals(ethereum.encoded, backing.values[ethereumSecretKey()])
        assertFalse(quarantineKey(ethereumSecretKey()) in backing.values)
    }

    @Test
    fun durableSanitizationFailureRollsBackAndNewProcessRetriesIdempotently() {
        val substrateKeypair = directSubstrateKeypair(EncryptionType.ED25519, 41)
        val encoded = SubstrateSecrets(
            substrateKeyPair = substrateKeypair,
            substrateDerivationPath = "//unbound"
        ).toHexString()
        createFixtureDatabase(
            WRITE_RETRY_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrateKeypair.publicKey,
                substrateCryptoType = CryptoType.ED25519.name,
                substrateAccountId = substrateKeypair.publicKey.substrateAccountId()
            )
        )
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = encoded
        }
        val failedPreferences = TestPreferences(backing).apply {
            durableFailure = DurableFailure(
                point = DurableFailurePoint.AFTER_REPLACE,
                key = substrateSecretKey()
            )
        }

        assertThrows(RuntimeException::class.java) {
            openProductionDatabase(WRITE_RETRY_DATABASE, failedPreferences)
        }

        assertEquals(76, rawDatabaseVersion(WRITE_RETRY_DATABASE))
        val sanitized = backing.values.getValue(substrateSecretKey())
        assertNull(
            SubstrateSecrets.read(sanitized)[SubstrateSecrets.SubstrateDerivationPath]
        )

        assertEquals(77, openProductionDatabase(WRITE_RETRY_DATABASE, TestPreferences(backing)))
        assertEquals(sanitized, backing.values[substrateSecretKey()])
        assertEquals(77, openProductionDatabase(WRITE_RETRY_DATABASE, TestPreferences(backing)))
    }

    @Test
    fun committedQuarantineWithSqlRollbackRetriesFromExactRecoveryEvidence() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        createFixtureDatabase(
            QUARANTINE_RETRY_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId()
            )
        )
        val exactCiphertext = "corrupt-exact-ciphertext"
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = exactCiphertext
        }
        val failedPreferences = TestPreferences(backing).apply {
            durableFailure = DurableFailure(
                point = DurableFailurePoint.AFTER_QUARANTINE,
                key = substrateSecretKey()
            )
        }

        assertThrows(RuntimeException::class.java) {
            openProductionDatabase(QUARANTINE_RETRY_DATABASE, failedPreferences)
        }

        assertEquals(76, rawDatabaseVersion(QUARANTINE_RETRY_DATABASE))
        assertFalse(substrateSecretKey() in backing.values)
        assertEquals(
            exactCiphertext,
            backing.values[quarantineKey(substrateSecretKey())]
        )

        assertEquals(
            77,
            openProductionDatabase(QUARANTINE_RETRY_DATABASE, TestPreferences(backing))
        )
        assertEquals(
            exactCiphertext,
            backing.values[quarantineKey(substrateSecretKey())]
        )
    }

    @Test
    fun partialEthereumDatabaseIdentityMarksRecoveryWithoutMovingActiveSecret() {
        val ethereum = ethereumFixture()
        createFixtureDatabase(
            PARTIAL_IDENTITY_DATABASE,
            76,
            FixtureIdentity(ethereumPublicKey = ethereum.keypair.publicKey)
        )
        val backing = SecretBacking().apply {
            values[ethereumSecretKey()] = ethereum.encoded
        }

        assertEquals(
            77,
            openProductionDatabase(
                PARTIAL_IDENTITY_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(ethereum.encoded, backing.values[ethereumSecretKey()])
        assertFalse(quarantineKey(ethereumSecretKey()) in backing.values)
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            backing.values[publicIdentityMarker(ethereumSecretKey())]
        )
        assertEquals(
            77,
            openProductionDatabase(
                PARTIAL_IDENTITY_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(ethereum.encoded, backing.values[ethereumSecretKey()])
    }

    @Test
    fun unknownSubstrateCryptoTypeMarksRecoveryWithoutMovingActiveSecret() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        createFixtureDatabase(
            UNKNOWN_CRYPTO_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = "FUTURE_CRYPTO",
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId()
            )
        )
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = substrate.encoded
        }

        assertEquals(
            77,
            openProductionDatabase(
                UNKNOWN_CRYPTO_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(substrate.encoded, backing.values[substrateSecretKey()])
        assertFalse(quarantineKey(substrateSecretKey()) in backing.values)
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            backing.values[publicIdentityMarker(substrateSecretKey())]
        )
        assertEquals(
            77,
            openProductionDatabase(
                UNKNOWN_CRYPTO_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(substrate.encoded, backing.values[substrateSecretKey()])
    }

    @Test
    fun identityMarkerCommitFailureRollsBackWithoutTouchingActiveSecret() {
        val ethereum = ethereumFixture()
        createFixtureDatabase(
            IDENTITY_MARKER_FAILURE_DATABASE,
            76,
            FixtureIdentity(ethereumPublicKey = ethereum.keypair.publicKey)
        )
        val markerKey = publicIdentityMarker(ethereumSecretKey())
        val backing = SecretBacking().apply {
            values[ethereumSecretKey()] = ethereum.encoded
        }
        val failedPreferences = TestPreferences(backing).apply {
            durableFailure = DurableFailure(
                point = DurableFailurePoint.BEFORE_REPLACE,
                key = markerKey
            )
        }

        assertThrows(RuntimeException::class.java) {
            openProductionDatabase(
                IDENTITY_MARKER_FAILURE_DATABASE,
                failedPreferences
            )
        }

        assertEquals(76, rawDatabaseVersion(IDENTITY_MARKER_FAILURE_DATABASE))
        assertEquals(ethereum.encoded, backing.values[ethereumSecretKey()])
        assertFalse(markerKey in backing.values)
        assertFalse(quarantineKey(ethereumSecretKey()) in backing.values)
    }

    @Test
    fun committedIdentityMarkerSurvivesSqlRollbackAndRetryIsIdempotent() {
        val ethereum = ethereumFixture()
        createFixtureDatabase(
            IDENTITY_MARKER_RETRY_DATABASE,
            76,
            FixtureIdentity(ethereumPublicKey = ethereum.keypair.publicKey)
        )
        val markerKey = publicIdentityMarker(ethereumSecretKey())
        val backing = SecretBacking().apply {
            values[ethereumSecretKey()] = ethereum.encoded
        }
        val failedPreferences = TestPreferences(backing).apply {
            durableFailure = DurableFailure(
                point = DurableFailurePoint.AFTER_REPLACE,
                key = markerKey
            )
        }

        assertThrows(RuntimeException::class.java) {
            openProductionDatabase(
                IDENTITY_MARKER_RETRY_DATABASE,
                failedPreferences
            )
        }

        assertEquals(76, rawDatabaseVersion(IDENTITY_MARKER_RETRY_DATABASE))
        assertEquals(ethereum.encoded, backing.values[ethereumSecretKey()])
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            backing.values[markerKey]
        )
        assertEquals(
            77,
            openProductionDatabase(
                IDENTITY_MARKER_RETRY_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(ethereum.encoded, backing.values[ethereumSecretKey()])
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            backing.values[markerKey]
        )
    }

    @Test
    fun malformedWatchOnlyIdentityWithoutSecretOpensWithoutRecoveryMarker() {
        val malformedPublicKey = ByteArray(64) { (it + 1).toByte() }
        createFixtureDatabase(
            MALFORMED_WATCH_ONLY_DATABASE,
            76,
            FixtureIdentity(ethereumPublicKey = malformedPublicKey)
        )
        val backing = SecretBacking()

        assertEquals(
            77,
            openProductionDatabase(
                MALFORMED_WATCH_ONLY_DATABASE,
                TestPreferences(backing)
            )
        )

        assertTrue(backing.values.isEmpty())
        assertFalse(
            publicIdentityMarker(ethereumSecretKey()) in backing.values
        )
    }

    @Test
    fun interruptedAddEvmSecretWithAbsentDatabaseIdentitySurvivesVersion77Open() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        val stagedEthereum = ethereumFixture()
        createFixtureDatabase(
            STAGED_ADD_EVM_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId()
            )
        )
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = substrate.encoded
        }
        val preferences = TestPreferences(backing)
        val substrateAccountId = substrate.keypair.publicKey.substrateAccountId()
        val ethereumAddress =
            stagedEthereum.keypair.publicKey.ethereumAddressFromPublicKey()
        val journalCryptoType =
            WalletSecretMutationJournalStore.SubstrateCryptoType.SR25519
        val beforeImage = PublicAfterImage(
            name = "Migration fixture \uD83D\uDD10",
            substratePublicKeyHex = substrate.keypair.publicKey.toPlainHexString(),
            substrateAccountIdHex = substrateAccountId.toPlainHexString(),
            substrateCryptoType = journalCryptoType,
            ethereumPublicKeyHex = null,
            ethereumAddressHex = null,
            tonPublicKeyHex = null,
            isSelected = true,
            position = 0,
            isBackedUp = true,
            googleBackupAddress = null,
            initialized = true
        )
        WalletSecretMutationJournalStore(preferences).stage(
            journal = Journal(
                operationId = STAGED_ADD_EVM_OPERATION_ID,
                operation = Operation.ADD_EVM,
                metaId = META_ID,
                beforeImage = beforeImage,
                afterImage = beforeImage.copy(
                    ethereumPublicKeyHex = stagedEthereum.keypair.publicKey
                        .toPlainHexString(),
                    ethereumAddressHex = ethereumAddress.toPlainHexString()
                ),
                selectedMetaIdAfterDelete = null,
                chainAccountIdsHex = emptySet(),
                secretKeysToPut = setOf(ethereumSecretKey()),
                secretKeysToRemove = emptySet()
            ),
            finalSecretPlaintexts = mapOf(
                ethereumSecretKey() to stagedEthereum.encoded
            )
        )

        assertEquals(
            77,
            openProductionDatabase(STAGED_ADD_EVM_DATABASE, preferences)
        )

        assertEquals(stagedEthereum.encoded, backing.values[ethereumSecretKey()])
        assertFalse(quarantineKey(ethereumSecretKey()) in backing.values)
        assertFalse(publicIdentityMarker(ethereumSecretKey()) in backing.values)
    }

    @Test
    fun wrongSqliteStorageClassesAndMalformedPayloadMarkRecoveryWithoutQuarantine() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        createFixtureDatabase(
            WRONG_STORAGE_CLASS_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId()
            )
        )
        mutateDatabase(WRONG_STORAGE_CLASS_DATABASE) {
            execSQL(
                "UPDATE meta_accounts SET substratePublicKey = ?, " +
                    "substrateCryptoType = ? WHERE id = ?",
                arrayOf(
                    "\u00E9".repeat(32),
                    CryptoType.SR25519.name.encodeToByteArray(),
                    META_ID
                )
            )
        }
        val malformed = "malformed-before-identity-\u0000-\uD83D\uDD10"
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = malformed
        }

        assertEquals(
            77,
            openProductionDatabase(
                WRONG_STORAGE_CLASS_DATABASE,
                TestPreferences(backing)
            )
        )

        assertEquals(malformed, backing.values[substrateSecretKey()])
        assertFalse(quarantineKey(substrateSecretKey()) in backing.values)
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            backing.values[publicIdentityMarker(substrateSecretKey())]
        )
    }

    @Test
    fun invalidEthereumPrefixAndMalformedPayloadMarkRecoveryWithoutQuarantine() {
        val ethereum = ethereumFixture()
        val invalidPublicKey = ethereum.keypair.publicKey.clone().apply {
            this[0] = 0x04
        }
        val validAddress =
            ethereum.keypair.publicKey.ethereumAddressFromPublicKey()
        createFixtureDatabase(
            INVALID_ETHEREUM_PREFIX_DATABASE,
            76,
            FixtureIdentity(
                ethereumPublicKey = invalidPublicKey,
                ethereumAddress = validAddress
            )
        )
        val malformed = "malformed-ethereum-before-identity-\u0000"
        val backing = SecretBacking().apply {
            values[ethereumSecretKey()] = malformed
        }

        assertEquals(
            77,
            openProductionDatabase(
                INVALID_ETHEREUM_PREFIX_DATABASE,
                TestPreferences(backing)
            )
        )

        assertEquals(malformed, backing.values[ethereumSecretKey()])
        assertFalse(quarantineKey(ethereumSecretKey()) in backing.values)
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            backing.values[publicIdentityMarker(ethereumSecretKey())]
        )
    }

    @Test
    fun offCurveEthereumPublicKeyMarksRecoveryAndSurvivesTwoLaunches() {
        val ethereum = ethereumFixture()
        val offCurvePublicKey =
            byteArrayOf(0x02) + ByteArray(32) { 0xFF.toByte() }
        createFixtureDatabase(
            OFF_CURVE_ETHEREUM_DATABASE,
            76,
            FixtureIdentity(
                ethereumPublicKey = offCurvePublicKey,
                ethereumAddress =
                    ethereum.keypair.publicKey.ethereumAddressFromPublicKey()
            )
        )
        val backing = SecretBacking().apply {
            values[ethereumSecretKey()] = ethereum.encoded
        }
        val markerKey = publicIdentityMarker(ethereumSecretKey())

        repeat(2) {
            assertEquals(
                77,
                openProductionDatabase(
                    OFF_CURVE_ETHEREUM_DATABASE,
                    TestPreferences(backing)
                )
            )
            assertEquals(
                ethereum.encoded,
                backing.values[ethereumSecretKey()]
            )
            assertFalse(quarantineKey(ethereumSecretKey()) in backing.values)
            assertEquals(
                WalletPublicIdentityRecovery.MARKER_VALUE,
                backing.values[markerKey]
            )
        }
    }

    @Test
    fun missingAddressBookIndexPreservesPreferencesAndVersion76() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        createFixtureDatabase(
            MISSING_ADDRESS_BOOK_INDEX_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId =
                    substrate.keypair.publicKey.substrateAccountId()
            )
        )
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = substrate.encoded
        }
        val preferences = TestPreferences(backing)
        val exactBefore = backing.values.toMap()
        mutateDatabase(MISSING_ADDRESS_BOOK_INDEX_DATABASE) {
            execSQL("DROP INDEX index_address_book_address_chainId")
        }

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            runIntegrityMigrationDirectly(
                databaseName = MISSING_ADDRESS_BOOK_INDEX_DATABASE,
                preferences = preferences
            )
        }

        assertEquals(76, rawDatabaseVersion(MISSING_ADDRESS_BOOK_INDEX_DATABASE))
        assertEquals(exactBefore, backing.values)
        assertEquals(0, preferences.snapshotBoundReplaceCount)
        assertEquals(0, preferences.durableWriteCount)

        mutateDatabase(MISSING_ADDRESS_BOOK_INDEX_DATABASE) {
            execSQL(
                "CREATE UNIQUE INDEX " +
                    "index_address_book_address_chainId " +
                    "ON address_book(address, chainId)"
            )
        }
        assertEquals(
            77,
            openProductionDatabase(
                MISSING_ADDRESS_BOOK_INDEX_DATABASE,
                preferences
            )
        )
    }

    @Test
    fun injectedExactWalletRowLimitRunsIntegrityMigration() {
        createFixtureDatabase(
            EXACT_WALLET_ROW_LIMIT_DATABASE,
            76,
            FixtureIdentity()
        )
        addWatchOnlyWallet(
            databaseName = EXACT_WALLET_ROW_LIMIT_DATABASE,
            metaId = SECOND_META_ID
        )
        val preferences = AccessCountingPreferences()

        runIntegrityMigrationDirectly(
            databaseName = EXACT_WALLET_ROW_LIMIT_DATABASE,
            preferences = preferences,
            rowLimits = WalletMigrationRowLimits(
                maxWalletRows = 2,
                maxChainAccountRows = 1
            )
        )

        assertTrue(preferences.healthCheckCount > 0)
        assertTrue(preferences.readCount > 0)
        assertEquals(0, preferences.writeCount)
    }

    @Test
    fun injectedWalletRowLimitPlusOneFailsBeforePreferenceAccess() {
        createFixtureDatabase(
            EXCEEDED_WALLET_ROW_LIMIT_DATABASE,
            76,
            FixtureIdentity()
        )
        addWatchOnlyWallet(
            databaseName = EXCEEDED_WALLET_ROW_LIMIT_DATABASE,
            metaId = SECOND_META_ID
        )
        val preferences = AccessCountingPreferences()

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            runIntegrityMigrationDirectly(
                databaseName = EXCEEDED_WALLET_ROW_LIMIT_DATABASE,
                preferences = preferences,
                rowLimits = WalletMigrationRowLimits(
                    maxWalletRows = 1,
                    maxChainAccountRows = 1
                )
            )
        }

        assertTrue(preferences.healthCheckCount > 0)
        assertEquals(0, preferences.readCount)
        assertEquals(0, preferences.writeCount)
        assertEquals(76, rawDatabaseVersion(EXCEEDED_WALLET_ROW_LIMIT_DATABASE))
    }

    @Test
    fun injectedExactChainAccountRowLimitRunsIntegrityMigration() {
        createFixtureDatabase(
            EXACT_CHAIN_ROW_LIMIT_DATABASE,
            76,
            FixtureIdentity()
        )
        repeat(2) { index ->
            val keypair = directSubstrateKeypair(
                encryptionType = EncryptionType.SR25519,
                variant = 91 + index
            )
            addChainAccount(
                databaseName = EXACT_CHAIN_ROW_LIMIT_DATABASE,
                publicKey = keypair.publicKey,
                accountId = keypair.publicKey.substrateAccountId(),
                cryptoType = CryptoType.SR25519,
                chainId = "$CHAIN_ROW_LIMIT_PREFIX-$index"
            )
        }
        val preferences = AccessCountingPreferences()

        runIntegrityMigrationDirectly(
            databaseName = EXACT_CHAIN_ROW_LIMIT_DATABASE,
            preferences = preferences,
            rowLimits = WalletMigrationRowLimits(
                maxWalletRows = 1,
                maxChainAccountRows = 2
            )
        )

        assertTrue(preferences.healthCheckCount > 0)
        assertTrue(preferences.readCount > 0)
        assertEquals(0, preferences.writeCount)
    }

    @Test
    fun injectedChainAccountRowLimitPlusOneFailsBeforePreferenceAccess() {
        createFixtureDatabase(
            EXCEEDED_CHAIN_ROW_LIMIT_DATABASE,
            76,
            FixtureIdentity()
        )
        repeat(2) { index ->
            val keypair = directSubstrateKeypair(
                encryptionType = EncryptionType.SR25519,
                variant = 93 + index
            )
            addChainAccount(
                databaseName = EXCEEDED_CHAIN_ROW_LIMIT_DATABASE,
                publicKey = keypair.publicKey,
                accountId = keypair.publicKey.substrateAccountId(),
                cryptoType = CryptoType.SR25519,
                chainId = "$CHAIN_ROW_LIMIT_PREFIX-overflow-$index"
            )
        }
        val preferences = AccessCountingPreferences()

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            runIntegrityMigrationDirectly(
                databaseName = EXCEEDED_CHAIN_ROW_LIMIT_DATABASE,
                preferences = preferences,
                rowLimits = WalletMigrationRowLimits(
                    maxWalletRows = 1,
                    maxChainAccountRows = 1
                )
            )
        }

        assertTrue(preferences.healthCheckCount > 0)
        assertEquals(0, preferences.readCount)
        assertEquals(0, preferences.writeCount)
        assertEquals(76, rawDatabaseVersion(EXCEEDED_CHAIN_ROW_LIMIT_DATABASE))
    }

    @Test
    fun attackerSizedWrongTypeChainMetaIdFailsBeforePreferenceAccess() {
        val keypair = directSubstrateKeypair(
            encryptionType = EncryptionType.SR25519,
            variant = 95
        )
        createFixtureDatabase(
            WRONG_TYPE_CHAIN_META_ID_DATABASE,
            76,
            FixtureIdentity()
        )
        addChainAccount(
            databaseName = WRONG_TYPE_CHAIN_META_ID_DATABASE,
            publicKey = keypair.publicKey,
            accountId = keypair.publicKey.substrateAccountId(),
            cryptoType = CryptoType.SR25519
        )
        mutateDatabase(WRONG_TYPE_CHAIN_META_ID_DATABASE) {
            execSQL(
                "UPDATE chain_accounts SET metaId = zeroblob(?) " +
                    "WHERE chainId = ?",
                arrayOf(ATTACKER_DB_IDENTITY_BYTES, CHAIN_ID)
            )
        }
        val preferences = AccessCountingPreferences()

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            runIntegrityMigrationDirectly(
                databaseName = WRONG_TYPE_CHAIN_META_ID_DATABASE,
                preferences = preferences
            )
        }

        assertTrue(preferences.healthCheckCount > 0)
        assertEquals(0, preferences.readCount)
        assertEquals(0, preferences.writeCount)
        assertEquals(
            76,
            rawDatabaseVersion(WRONG_TYPE_CHAIN_META_ID_DATABASE)
        )
    }

    @Test
    fun nonPositiveWalletIdFailsPreflightWithoutAnyPreferenceMutation() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        createFixtureDatabase(
            INVALID_META_ID_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId()
            )
        )
        mutateDatabase(INVALID_META_ID_DATABASE) {
            execSQL(
                "UPDATE meta_accounts SET id = ? WHERE id = ?",
                arrayOf(-1L, META_ID)
            )
        }
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = substrate.encoded
        }
        val exactBefore = backing.values.toMap()

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            openProductionDatabase(
                INVALID_META_ID_DATABASE,
                TestPreferences(backing)
            )
        }

        assertEquals(76, rawDatabaseVersion(INVALID_META_ID_DATABASE))
        assertEquals(exactBefore, backing.values)
    }

    @Test
    fun invalidExistingRecoveryMarkerFailsBeforeWritesAndValidRetryIsIdempotent() {
        val substrate = substrateFixture(EncryptionType.SR25519)
        createFixtureDatabase(
            INVALID_EXISTING_MARKER_DATABASE,
            76,
            FixtureIdentity(
                substratePublicKey = substrate.keypair.publicKey,
                substrateCryptoType = CryptoType.SR25519.name,
                substrateAccountId = substrate.keypair.publicKey.substrateAccountId()
            )
        )
        val markerKey = publicIdentityMarker(substrateSecretKey())
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = substrate.encoded
            values[markerKey] = "invalid-public-identity-marker"
        }
        val exactBefore = backing.values.toMap()

        assertThrows(IllegalStateException::class.java) {
            openProductionDatabase(
                INVALID_EXISTING_MARKER_DATABASE,
                TestPreferences(backing)
            )
        }
        assertEquals(76, rawDatabaseVersion(INVALID_EXISTING_MARKER_DATABASE))
        assertEquals(exactBefore, backing.values)

        backing.values[markerKey] =
            WalletPublicIdentityRecovery.MARKER_VALUE
        assertEquals(
            77,
            openProductionDatabase(
                INVALID_EXISTING_MARKER_DATABASE,
                TestPreferences(backing)
            )
        )
        val afterRecovery = backing.values.toMap()
        assertEquals(
            77,
            openProductionDatabase(
                INVALID_EXISTING_MARKER_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(afterRecovery, backing.values)
        assertEquals(substrate.encoded, backing.values[substrateSecretKey()])
    }

    private fun assertSubstratePrivateMismatch(
        databaseName: String,
        encryptionType: EncryptionType,
        cryptoType: CryptoType
    ) {
        val valid = directSubstrateKeypair(encryptionType, 51)
        val other = directSubstrateKeypair(encryptionType, 52)
        val forged = Keypair(
            publicKey = valid.publicKey,
            privateKey = other.privateKey,
            nonce = (valid as? Sr25519Keypair)?.nonce
        )
        val encoded = SubstrateSecrets(
            substrateKeyPair = forged
        ).toHexString()
        createFixtureDatabase(
            databaseName,
            76,
            FixtureIdentity(
                substratePublicKey = valid.publicKey,
                substrateCryptoType = cryptoType.name,
                substrateAccountId = valid.publicKey.substrateAccountId()
            )
        )
        val backing = SecretBacking().apply {
            values[substrateSecretKey()] = encoded
        }

        assertEquals(77, openProductionDatabase(databaseName, TestPreferences(backing)))

        assertFalse(substrateSecretKey() in backing.values)
        assertEquals(encoded, backing.values[quarantineKey(substrateSecretKey())])
    }

    private fun createFixtureDatabase(
        databaseName: String,
        version: Int,
        identity: FixtureIdentity
    ) {
        createdDatabases += databaseName
        helper.createDatabase(databaseName, version).apply {
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
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    META_ID,
                    identity.substratePublicKey,
                    identity.substrateCryptoType,
                    identity.substrateAccountId,
                    identity.ethereumPublicKey,
                    identity.ethereumAddress,
                    identity.tonPublicKey,
                    "Migration fixture \uD83D\uDD10",
                    1,
                    0,
                    1,
                    null,
                    1
                )
            )
            close()
        }
    }

    private fun openProductionDatabase(
        databaseName: String,
        preferences: EncryptedPreferences
    ): Int {
        var database: AppDatabase? = null
        try {
            database = AppDatabase.create(
                context = context,
                databaseName = databaseName,
                storeV1 = SecretStoreV1Impl(preferences),
                storeV2 = SecretStoreV2(preferences),
                encryptedPreferences = preferences,
                substrateSecretStore = SubstrateSecretStore(preferences),
                ethereumSecretStore = EthereumSecretStore(preferences)
            )
            return database.openHelper.writableDatabase.version
        } finally {
            database?.close()
        }
    }

    private fun assertIntegrityMigrationRejectsConcurrentMutation(
        databaseName: String,
        preferences: EncryptedPreferences
    ) {
        // Android's SQLite opener retries a failed upgrade immediately. A
        // one-shot concurrent mutation can therefore be revalidated and
        // migrated successfully before openProductionDatabase returns. Run
        // the first attempt explicitly so this assertion observes the stale
        // CAS rejection itself; each test then exercises the clean production
        // retry separately.
        assertThrows(WalletSecretConcurrentMutationException::class.java) {
            runIntegrityMigrationDirectly(
                databaseName = databaseName,
                preferences = preferences
            )
        }
    }

    private fun runIntegrityMigrationDirectly(
        databaseName: String,
        preferences: EncryptedPreferences,
        rowLimits: WalletMigrationRowLimits =
            WalletMigrationRowLimits.PRODUCTION
    ) {
        val callback = object : SupportSQLiteOpenHelper.Callback(76) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("The version 76 migration fixture was not installed")
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                error(
                    "Unexpected fixture setup migration from " +
                        "$oldVersion to $newVersion"
                )
            }
        }
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        ).use { openHelper ->
            openHelper.writableDatabase.apply {
                setForeignKeyConstraintsEnabled(true)
                beginTransaction()
                try {
                    WalletSecretIntegrityMigration(
                        encryptedPreferences = preferences,
                        rowLimits = rowLimits
                    ).migrate(this)
                    setTransactionSuccessful()
                } finally {
                    endTransaction()
                }
            }
        }
    }

    private fun rawDatabaseVersion(databaseName: String): Int {
        return SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use(SQLiteDatabase::getVersion)
    }

    private fun mutateDatabase(
        databaseName: String,
        action: SQLiteDatabase.() -> Unit
    ) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use(action)
    }

    private fun addChainAccount(
        databaseName: String,
        publicKey: ByteArray,
        accountId: ByteArray,
        cryptoType: CryptoType,
        chainId: String = CHAIN_ID
    ) {
        mutateDatabase(databaseName) {
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
                    chainId,
                    "CAS chain fixture",
                    "cas-chain-icon",
                    42,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "SUBSTRATE"
                )
            )
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
                    chainId,
                    publicKey,
                    accountId,
                    cryptoType.name,
                    "CAS chain signing account",
                    1
                )
            )
        }
    }

    private fun addWatchOnlyWallet(
        databaseName: String,
        metaId: Long
    ) {
        mutateDatabase(databaseName) {
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
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    metaId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Row-limit watch-only fixture",
                    0,
                    1,
                    0,
                    null,
                    1
                )
            )
        }
    }

    private fun substrateFixture(encryptionType: EncryptionType): SubstrateFixture {
        val decodedPath = SubstrateJunctionDecoder.decode(SUBSTRATE_PATH)
        val seed = SubstrateSeedFactory.deriveSeed32(
            mnemonicWords = MNEMONIC.words,
            password = decodedPath.password
        ).seed
        val keypair = SubstrateKeypairFactory.generate(
            encryptionType = encryptionType,
            seed = seed,
            junctions = decodedPath.junctions
        )
        return SubstrateFixture(
            keypair = keypair,
            encoded = SubstrateSecrets(
                substrateKeyPair = keypair,
                entropy = ENTROPY,
                seed = seed,
                substrateDerivationPath = SUBSTRATE_PATH
            ).toHexString()
        )
    }

    private fun ethereumFixture(): EthereumFixture {
        val decodedPath = BIP32JunctionDecoder.decode(ETHEREUM_PATH)
        val seed = EthereumSeedFactory.deriveSeed32(
            mnemonicWords = MNEMONIC.words,
            password = decodedPath.password
        ).seed
        val keypair = EthereumKeypairFactory.generate(
            seed = seed,
            junctions = decodedPath.junctions
        )
        return EthereumFixture(
            keypair = keypair,
            encoded = EthereumSecrets(
                entropy = ENTROPY,
                seed = keypair.privateKey,
                ethereumKeypair = keypair,
                ethereumDerivationPath = ETHEREUM_PATH
            ).toHexString()
        )
    }

    private fun directSubstrateKeypair(
        encryptionType: EncryptionType,
        variant: Int
    ): FearlessKeypair {
        return SubstrateKeypairFactory.generate(
            encryptionType = encryptionType,
            seed = privateKey(variant),
            junctions = if (encryptionType == EncryptionType.ECDSA) {
                SubstrateJunctionDecoder.decode("//hard").junctions
            } else {
                emptyList()
            }
        )
    }

    private fun directEthereumKeypair(variant: Int): FearlessKeypair {
        return EthereumKeypairFactory.createWithPrivateKey(privateKey(variant))
    }

    private fun tonFixture(variant: Int): TonFixture {
        val mnemonic = MnemonicCreator.fromEntropy(
            ByteArray(16).apply { this[lastIndex] = variant.toByte() }
        ).words
        val seed = Mnemonic.toSeed(mnemonic.split(' '))
        val privateKey = PrivateKeyEd25519(seed)
        val publicKey = privateKey.publicKey()
        return TonFixture(
            publicKey = publicKey.key.toByteArray(),
            encoded = TonSecrets(
                seed = mnemonic.encodeToByteArray(),
                tonKeypair = Keypair(
                    publicKey = publicKey.key.toByteArray(),
                    privateKey = privateKey.key.toByteArray()
                )
            ).toHexString()
        )
    }

    private fun legacySecrets(
        substrateKeypair: FearlessKeypair,
        ethereumKeypair: FearlessKeypair,
        entropy: ByteArray? = ENTROPY,
        substrateSeed: ByteArray? = defaultSubstrateSeed(),
        substratePath: String? = SUBSTRATE_PATH,
        ethereumPath: String? = ETHEREUM_PATH
    ): String {
        return TonMigration.MetaAccountSecretsV69 { secrets ->
            secrets[Entropy] = entropy
            secrets[Seed] = substrateSeed
            secrets[SubstrateKeypair] = KeyPairSchema { keypair ->
                keypair[PublicKey] = substrateKeypair.publicKey
                keypair[PrivateKey] = substrateKeypair.privateKey
                keypair[Nonce] = (substrateKeypair as? Sr25519Keypair)?.nonce
            }
            secrets[SubstrateDerivationPath] = substratePath
            secrets[EthereumKeypair] = KeyPairSchema { keypair ->
                keypair[PublicKey] = ethereumKeypair.publicKey
                keypair[PrivateKey] = ethereumKeypair.privateKey
                keypair[Nonce] = null
            }
            secrets[EthereumDerivationPath] = ethereumPath
        }.toHexString()
    }

    private fun defaultSubstrateSeed(): ByteArray {
        val decodedPath = SubstrateJunctionDecoder.decode(SUBSTRATE_PATH)
        return SubstrateSeedFactory.deriveSeed32(
            mnemonicWords = MNEMONIC.words,
            password = decodedPath.password
        ).seed
    }

    private fun privateKey(variant: Int): ByteArray {
        return ByteArray(32) { index ->
            ((index + 1) * 7 + variant).toByte()
        }
    }

    private data class FixtureIdentity(
        val substratePublicKey: ByteArray? = null,
        val substrateCryptoType: String? = null,
        val substrateAccountId: ByteArray? = null,
        val ethereumPublicKey: ByteArray? = null,
        val ethereumAddress: ByteArray? = null,
        val tonPublicKey: ByteArray? = null
    )

    private data class SubstrateFixture(
        val keypair: FearlessKeypair,
        val encoded: String
    )

    private data class EthereumFixture(
        val keypair: FearlessKeypair,
        val encoded: String
    )

    private data class TonFixture(
        val publicKey: ByteArray,
        val encoded: String
    )

    private class SecretBacking {
        val values = linkedMapOf<String, String>()
    }

    private enum class DurableFailurePoint {
        BEFORE_REPLACE,
        AFTER_REPLACE,
        AFTER_QUARANTINE
    }

    private data class DurableFailure(
        val point: DurableFailurePoint,
        val key: String
    )

    private class TestPreferences(
        private val backing: SecretBacking
    ) : EncryptedPreferences {

        var genericReadFailureKey: String? = null
        var secureReadFailureKey: String? = null
        var durableFailure: DurableFailure? = null
        var beforeNextSnapshotBoundReplace: (() -> Unit)? = null
        var snapshotBoundReplaceCount: Int = 0
            private set
        var durableWriteCount: Int = 0
            private set
        private var durableStorageHealthy = true

        override fun putEncryptedString(field: String, value: String) {
            replaceEncryptedStringsDurably(
                valuesToPut = mapOf(field to value),
                keysToRemove = emptySet()
            )
        }

        override fun getDecryptedString(field: String): String? {
            if (field == secureReadFailureKey) {
                throw WalletSecureStorageUnavailableException(
                    "Injected secure-storage failure"
                )
            }
            if (field == genericReadFailureKey) {
                throw RuntimeException("Injected generic preference read failure")
            }
            return backing.values[field]
        }

        override fun hasKey(field: String): Boolean = field in backing.values

        override fun hasKeyWithPrefix(prefix: String): Boolean {
            return backing.values.keys.any { it.startsWith(prefix) }
        }

        override fun keysWithPrefixes(
            prefixes: Set<String>,
            maxResultCount: Int,
            maxKeyBytes: Int,
            maxTotalKeyBytes: Int,
            failOnOversizedMatch: Boolean
        ): Set<String> = boundedTestPreferenceKeys(
            keys = backing.values.keys,
            prefixes = prefixes,
            maxResultCount = maxResultCount,
            maxKeyBytes = maxKeyBytes,
            maxTotalKeyBytes = maxTotalKeyBytes,
            failOnOversizedMatch = failOnOversizedMatch
        )

        override fun removeKey(field: String) {
            backing.values.remove(field)
        }

        override fun replaceEncryptedStringsDurablyIfStatesMatch(
            expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>,
            snapshotMoves: List<EncryptedPreferenceSnapshotMove>
        ): Boolean {
            snapshotBoundReplaceCount += 1
            beforeNextSnapshotBoundReplace?.let { interleave ->
                beforeNextSnapshotBoundReplace = null
                interleave()
            }
            return super<EncryptedPreferences>.replaceEncryptedStringsDurablyIfStatesMatch(
                expectedStates = expectedStates,
                valuesToPut = valuesToPut,
                keysToRemove = keysToRemove,
                snapshotMoves = snapshotMoves
            )
        }

        override fun replaceEncryptedStringsDurably(
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>
        ) {
            durableWriteCount += 1
            requireDurableStorageHealthy()
            val failure = durableFailure
            if (
                failure?.point == DurableFailurePoint.BEFORE_REPLACE &&
                (
                    failure.key in valuesToPut ||
                        failure.key in keysToRemove
                    )
            ) {
                durableStorageHealthy = false
                throw RuntimeException("Injected failure before durable replacement")
            }
            backing.values.putAll(valuesToPut)
            keysToRemove.forEach(backing.values::remove)
            if (
                failure?.point == DurableFailurePoint.AFTER_REPLACE &&
                (
                    failure.key in valuesToPut ||
                        failure.key in keysToRemove
                    )
            ) {
                durableStorageHealthy = false
                throw RuntimeException("Injected failure after durable replacement")
            }
        }

        override fun quarantineEncryptedStringDurably(
            sourceKey: String,
            quarantineKey: String,
            expectedSnapshot: EncryptedPreferenceSnapshot
        ): Boolean {
            requireDurableStorageHealthy()
            val source = backing.values[sourceKey]
            if (source == null) {
                check(quarantineKey in backing.values)
                return expectedSnapshot.matchesUnencryptedStorageValue(
                    checkNotNull(backing.values[quarantineKey])
                )
            }
            if (!expectedSnapshot.matchesUnencryptedStorageValue(source)) {
                return false
            }
            val existing = backing.values[quarantineKey]
            check(existing == null || existing == source)
            backing.values[quarantineKey] = source
            backing.values.remove(sourceKey)
            val failure = durableFailure
            if (
                failure?.point == DurableFailurePoint.AFTER_QUARANTINE &&
                failure.key == sourceKey
            ) {
                durableStorageHealthy = false
                throw RuntimeException("Injected failure after durable quarantine")
            }
            return true
        }

        override fun requireDurableStorageHealthy() {
            check(durableStorageHealthy) {
                "Injected durable storage health latch"
            }
        }
    }

    private class AccessCountingPreferences : EncryptedPreferences {

        var healthCheckCount: Int = 0
            private set
        var readCount: Int = 0
            private set
        var writeCount: Int = 0
            private set

        override fun putEncryptedString(field: String, value: String) {
            writeCount += 1
        }

        override fun getDecryptedString(field: String): String? {
            readCount += 1
            return null
        }

        override fun hasKey(field: String): Boolean {
            readCount += 1
            return false
        }

        override fun hasKeyWithPrefix(prefix: String): Boolean {
            readCount += 1
            return false
        }

        override fun keysWithPrefixes(
            prefixes: Set<String>,
            maxResultCount: Int,
            maxKeyBytes: Int,
            maxTotalKeyBytes: Int,
            failOnOversizedMatch: Boolean
        ): Set<String> {
            readCount += 1
            return emptySet()
        }

        override fun removeKey(field: String) {
            writeCount += 1
        }

        override fun replaceEncryptedStringsDurably(
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>
        ) {
            writeCount += 1
        }

        override fun replaceEncryptedStringsDurablyIfStatesMatch(
            expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>,
            snapshotMoves: List<EncryptedPreferenceSnapshotMove>
        ): Boolean {
            writeCount += 1
            return false
        }

        override fun quarantineEncryptedStringDurably(
            sourceKey: String,
            quarantineKey: String,
            expectedSnapshot: EncryptedPreferenceSnapshot
        ): Boolean {
            writeCount += 1
            return false
        }

        override fun requireDurableStorageHealthy() {
            healthCheckCount += 1
        }
    }

    private companion object {
        const val META_ID = 77L
        const val CHAIN_ID = "wallet-integrity-cas-chain"
        const val SUBSTRATE_PATH = "//hard///password"
        const val ETHEREUM_PATH = "//44//60//0/0/0"

        const val VALID_ALL_DATABASE = "integrity-v76-all"
        const val VERSION_74_SANITIZE_DATABASE = "integrity-v74-sanitize"
        const val ENTROPY_NULL_DATABASE = "integrity-entropy-null"
        const val VERSION_76_LEGACY_DATABASE = "integrity-v76-legacy"
        const val VERSION_76_LEGACY_ENTROPY_NULL_DATABASE =
            "integrity-v76-legacy-entropy-null"
        const val VERSION_76_LEGACY_KEYPAIR_ONLY_DATABASE =
            "integrity-v76-legacy-keypair-only"
        const val LEGACY_REPLACEMENT_RETRY_DATABASE = "integrity-legacy-replacement-retry"
        const val LEGACY_SOURCE_RACE_DATABASE = "integrity-legacy-source-race"
        const val LEGACY_TARGET_CONFLICT_DATABASE =
            "integrity-legacy-target-conflict"
        const val LEGACY_TARGET_RACE_DATABASE = "integrity-legacy-target-race"
        const val LEGACY_PARTIAL_TARGET_DATABASE =
            "integrity-legacy-partial-target"
        const val CANONICAL_ROOT_RACE_DATABASE =
            "integrity-canonical-root-race"
        const val ROOT_SANITIZE_RACE_DATABASE =
            "integrity-root-sanitize-race"
        const val CHAIN_SANITIZE_RACE_DATABASE =
            "integrity-chain-sanitize-race"
        const val MARKER_RACE_DATABASE = "integrity-marker-race"
        const val SR25519_MISMATCH_DATABASE = "integrity-sr25519-mismatch"
        const val ED25519_MISMATCH_DATABASE = "integrity-ed25519-mismatch"
        const val ECDSA_MISMATCH_DATABASE = "integrity-ecdsa-mismatch"
        const val ETHEREUM_MISMATCH_DATABASE = "integrity-ethereum-mismatch"
        const val TON_MISMATCH_DATABASE = "integrity-ton-mismatch"
        const val ISOLATED_QUARANTINE_DATABASE = "integrity-isolated-quarantine"
        const val EXISTING_QUARANTINE_DATABASE = "integrity-existing-quarantine"
        const val READ_FAILURE_DATABASE = "integrity-read-failure"
        const val SECURE_STORAGE_FAILURE_DATABASE = "integrity-secure-storage-failure"
        const val WRITE_RETRY_DATABASE = "integrity-write-retry"
        const val QUARANTINE_RETRY_DATABASE = "integrity-quarantine-retry"
        const val PARTIAL_IDENTITY_DATABASE = "integrity-partial-identity"
        const val UNKNOWN_CRYPTO_DATABASE = "integrity-unknown-crypto"
        const val IDENTITY_MARKER_FAILURE_DATABASE =
            "integrity-identity-marker-failure"
        const val IDENTITY_MARKER_RETRY_DATABASE =
            "integrity-identity-marker-retry"
        const val MALFORMED_WATCH_ONLY_DATABASE =
            "integrity-malformed-watch-only"
        const val STAGED_ADD_EVM_DATABASE = "integrity-staged-add-evm"
        const val STAGED_ADD_EVM_OPERATION_ID =
            "123e4567-e89b-42d3-a456-426614174000"
        const val WRONG_STORAGE_CLASS_DATABASE =
            "integrity-wrong-storage-class"
        const val INVALID_ETHEREUM_PREFIX_DATABASE =
            "integrity-invalid-ethereum-prefix"
        const val OFF_CURVE_ETHEREUM_DATABASE =
            "integrity-off-curve-ethereum"
        const val INVALID_META_ID_DATABASE = "integrity-invalid-meta-id"
        const val MISSING_ADDRESS_BOOK_INDEX_DATABASE =
            "integrity-missing-address-book-index"
        const val INVALID_EXISTING_MARKER_DATABASE =
            "integrity-invalid-existing-marker"
        const val EXACT_WALLET_ROW_LIMIT_DATABASE =
            "integrity-exact-wallet-row-limit"
        const val EXCEEDED_WALLET_ROW_LIMIT_DATABASE =
            "integrity-exceeded-wallet-row-limit"
        const val EXACT_CHAIN_ROW_LIMIT_DATABASE =
            "integrity-exact-chain-row-limit"
        const val EXCEEDED_CHAIN_ROW_LIMIT_DATABASE =
            "integrity-exceeded-chain-row-limit"
        const val WRONG_TYPE_CHAIN_META_ID_DATABASE =
            "integrity-wrong-type-chain-meta-id"
        const val CHAIN_ROW_LIMIT_PREFIX = "integrity-row-limit-chain"
        const val SECOND_META_ID = META_ID + 1
        const val ATTACKER_DB_IDENTITY_BYTES = 8_388_608

        val ENTROPY = ByteArray(16) { (it * 7 + 3).toByte() }
        val MNEMONIC = MnemonicCreator.fromEntropy(ENTROPY)

        fun legacySecretKey(metaId: Long = META_ID) = "$metaId:ACCESS_SECRETS"
        fun substrateSecretKey(metaId: Long = META_ID) = "$metaId:SUBSTRATE_SECRETS"
        fun ethereumSecretKey(metaId: Long = META_ID) = "$metaId:ETHEREUM_SECRETS"
        fun tonSecretKey(metaId: Long = META_ID) = "$metaId:TON_SECRETS"
        fun chainSecretKey(accountId: ByteArray, metaId: Long = META_ID) =
            "$metaId:${accountId.toPlainHexString()}:ACCESS_SECRETS"
        fun publicIdentityMarker(
            activeKey: String,
            metaId: Long = META_ID
        ) = WalletPublicIdentityRecovery.keyFor(metaId, activeKey)
        fun quarantineKey(activeKey: String) = WalletSecretQuarantine.keyFor(activeKey)
    }
}

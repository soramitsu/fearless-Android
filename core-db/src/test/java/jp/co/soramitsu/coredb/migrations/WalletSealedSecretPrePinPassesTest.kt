package jp.co.soramitsu.coredb.migrations

import java.security.SecureRandom
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretEnvelope
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSealPolicy
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSession
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 4.3.2 seals wallet secrets under SK, which only an unlock opens. Before the PIN no start-up pass
 * may quarantine, archive, delete, convert or rewrite a sealed value, or move anything judged
 * against one; after the unlock the same passes act as before, by exact value.
 */
class WalletSealedSecretPrePinPassesTest {

    private val wallet = ShippedWalletFixtures.rawSeedDeclinedEthereum()
    private val identity = ShippedWalletFixtures.identity(META_ID, wallet)
    private val keyId = ByteArray(16).also(SecureRandom()::nextBytes)
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)

    @Before
    fun setUp() = WalletSecretSession.clear()

    @After
    fun tearDown() = WalletSecretSession.clear()

    private fun sealed(storageKey: String, plaintext: String): String {
        val target = checkNotNull(WalletSecretSealPolicy.classify(storageKey))
        return WalletSecretEnvelope.seal(
            key,
            WalletSecretEnvelope.Header(keyId, target.scope, target.logicalKey, ByteArray(0)),
            plaintext,
            SecureRandom()
        )
    }

    private fun unlock() = WalletSecretSession.open(keyId, key, WalletSecretSession.Origin.PIN_WRAP)

    private fun HashMapEncryptedPreferences.state(): Map<String, String?> = keysWithPrefixes(
        prefixes = setOf("$META_ID:", "9:", "77:", WalletSecretQuarantine.KEY_PREFIX, WalletSecretArchive.KEY_PREFIX,
            WalletPublicIdentityRecovery.KEY_PREFIX),
        maxResultCount = 64,
        maxKeyBytes = 512,
        maxTotalKeyBytes = 16_384
    ).associateWith(::getDecryptedString)

    private fun convertedSubstrate(): String = checkNotNull(
        WalletSecretIntegrityValidator.validateLegacyAndPrepareReplacement(
            encoded = wallet.accessSecretsHex,
            identity = identity
        ).substratePlaintext
    )

    @Test
    fun `a sealed quarantine of a deleted wallet is not archived before the PIN`() {
        val quarantine = WalletSecretQuarantine.keyFor("9:SUBSTRATE_SECRETS")
        val value = sealed(quarantine, convertedSubstrate())
        val preferences = HashMapEncryptedPreferences().apply { putEncryptedString(quarantine, value) }
        val before = preferences.state()

        val locked = WalletQuarantineRevalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))
        assertEquals(0, locked.archived)
        assertEquals(before, preferences.state())

        unlock()
        val unlocked = WalletQuarantineRevalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))
        assertEquals(1, unlocked.archived)
        assertEquals(value, preferences.getDecryptedString(WalletSecretArchive.keyFor("9:SUBSTRATE_SECRETS")))
    }

    @Test
    fun `a legacy payload next to a sealed V3 key is neither converted nor moved before the PIN`() {
        val access = WalletSecretQuarantine.keyFor(ACCESS)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(access, wallet.accessSecretsHex)
            putEncryptedString(SUBSTRATE, sealed(SUBSTRATE, convertedSubstrate()))
        }
        val before = preferences.state()

        WalletQuarantineRevalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))
        assertEquals(before, preferences.state())

        unlock()
        WalletQuarantineRevalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))
        // The sealed V3 key proves the identity: the legacy copy is retained in the archive, nothing rewritten.
        assertFalse(preferences.hasKey(access))
        assertEquals(wallet.accessSecretsHex, preferences.getDecryptedString(WalletSecretArchive.keyFor(ACCESS)))
        assertEquals(before[SUBSTRATE], preferences.getDecryptedString(SUBSTRATE))
    }

    @Test
    fun `a stale quarantine next to a sealed active key waits for the PIN`() {
        val quarantine = WalletSecretQuarantine.keyFor(SUBSTRATE)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(quarantine, convertedSubstrate())
            putEncryptedString(SUBSTRATE, sealed(SUBSTRATE, convertedSubstrate()))
        }
        val before = preferences.state()
        WalletQuarantineRevalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))
        assertEquals(before, preferences.state())

        unlock()
        val report = WalletQuarantineRevalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))
        assertEquals(1, report.archived)
        assertEquals(before[SUBSTRATE], preferences.getDecryptedString(SUBSTRATE))
    }

    @Test
    fun `a sealed archive is restored only after the PIN, by exact value`() {
        val archive = WalletSecretArchive.keyFor(SUBSTRATE)
        val value = sealed(archive, convertedSubstrate())
        val preferences = HashMapEncryptedPreferences().apply { putEncryptedString(archive, value) }
        WalletQuarantineRevalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))
        assertFalse(preferences.hasKey(SUBSTRATE))

        unlock()
        val report = WalletQuarantineRevalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))
        assertEquals(1, report.restored)
        assertEquals(value, preferences.getDecryptedString(SUBSTRATE))
    }

    @Test
    fun `a marker on a sealed key stays until the PIN lets this build judge it`() {
        val marker = WalletPublicIdentityRecovery.keyFor(META_ID, SUBSTRATE)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(SUBSTRATE, sealed(SUBSTRATE, convertedSubstrate()))
            putEncryptedString(marker, WalletPublicIdentityRecovery.MARKER_VALUE)
        }
        WalletQuarantineRevalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))
        assertTrue(preferences.hasKey(marker))

        unlock()
        WalletQuarantineRevalidation(preferences).reconcile(ShippedWalletFixtures.database(identity))
        assertFalse(preferences.hasKey(marker))
    }

    @Test
    fun `a sealed orphan is archived only after the PIN, by exact value`() {
        val orphan = "12:SUBSTRATE_SECRETS"
        val value = sealed(orphan, convertedSubstrate())
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(orphan, value)
            putEncryptedString(SUBSTRATE, convertedSubstrate())
        }
        val locked = WalletOrphanSecretInventory(preferences).reconcile(ShippedWalletFixtures.database(identity))
        assertEquals(0, locked.archivedCount)
        assertEquals(1, locked.deferredCount)
        assertEquals(value, preferences.getDecryptedString(orphan))

        unlock()
        val unlocked = WalletOrphanSecretInventory(preferences).reconcile(ShippedWalletFixtures.database(identity))
        assertEquals(1, unlocked.archivedCount)
        assertEquals(value, preferences.getDecryptedString(WalletSecretArchive.keyFor(orphan)))
    }

    @Test
    fun `the orphan pass never touches the wallet of a pending mutation journal`() {
        val staged = "77:SUBSTRATE_SECRETS"
        val preferences = HashMapEncryptedPreferences().apply { putEncryptedString(SUBSTRATE, convertedSubstrate()) }
        // A creation staged while unlocked, then the process died before the Room row.
        WalletSecretMutationJournalStore(preferences).stage(
            WalletSecretMutationJournalStore.Journal(
                operationId = "123e4567-e89b-42d3-a456-426614174000",
                operation = WalletSecretMutationJournalStore.Operation.CREATE,
                metaId = 77,
                beforeImage = null,
                afterImage = WalletSecretMutationJournalStore.PublicAfterImage(
                    name = "Pending",
                    substratePublicKeyHex = wallet.substratePublicKey.toHexString(),
                    substrateAccountIdHex = wallet.substrateAccountId.toHexString(),
                    substrateCryptoType = WalletSecretMutationJournalStore.SubstrateCryptoType.valueOf(wallet.substrateCryptoType.name),
                    ethereumPublicKeyHex = null,
                    ethereumAddressHex = null,
                    tonPublicKeyHex = null,
                    isSelected = false,
                    position = 1,
                    isBackedUp = false,
                    googleBackupAddress = null,
                    initialized = true
                ),
                selectedMetaIdAfterDelete = null,
                chainAccountIdsHex = emptySet(),
                secretKeysToPut = setOf(staged),
                secretKeysToRemove = emptySet()
            ),
            mapOf(staged to convertedSubstrate())
        )
        preferences.putEncryptedString(staged, sealed(staged, convertedSubstrate()))
        preferences.putEncryptedString("77:ETHEREUM_SECRETS", "raw value of the same pending wallet")
        val before = preferences.state()
        unlock()
        WalletOrphanSecretInventory(preferences).reconcile(ShippedWalletFixtures.database(identity))
        assertEquals(before, preferences.state())
    }

    @Test
    fun `migrations that hold wallet-secret storage all end before sealed values can exist`() {
        val preferences: EncryptedPreferences = HashMapEncryptedPreferences()
        val migrations = AppDatabase.migrations(
            storeV1 = SecretStoreV1Impl(preferences),
            storeV2 = SecretStoreV2(preferences),
            encryptedPreferences = preferences,
            substrateSecretStore = SubstrateSecretStore(preferences),
            ethereumSecretStore = EthereumSecretStore(preferences)
        )
        val secretTypes = listOf(
            EncryptedPreferences::class.java, jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1::class.java,
            SecretStoreV2::class.java, SubstrateSecretStore::class.java, EthereumSecretStore::class.java
        )
        val holdingSecrets = migrations.filter { migration ->
            generateSequence<Class<*>>(migration.javaClass) { it.superclass }.flatMap { it.declaredFields.asSequence() }
                .any { field -> secretTypes.any { it.isAssignableFrom(field.type) } }
        }
        assertEquals(4, holdingSecrets.size)
        // Envelopes are written only on database version 84 and later; these never run there, and
        // any later migration that held wallet-secret storage would be listed here and fail.
        holdingSecrets.forEach { assertTrue("${it.javaClass.simpleName} ends at ${it.endVersion}", it.endVersion <= 77) }
        assertTrue("Envelopes first exist on database version 84", APP_DATABASE_VERSION >= 84)
    }

    private companion object {
        const val META_ID = 5L
        const val ACCESS = "5:ACCESS_SECRETS"
        const val SUBSTRATE = "5:SUBSTRATE_SECRETS"
    }
}

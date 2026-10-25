package jp.co.soramitsu.account.impl.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Binds SR25519 wallets whose derivation path ends in a soft junction through
 * the real coordinator, Room and the SR25519 library shipped by the app.
 *
 * schnorrkel draws a fresh random nonce for every soft junction, so the
 * coordinator's re-derivation never reproduces the nonce the import stored.
 * All key material is synthetic.
 */
@RunWith(AndroidJUnit4::class)
class WalletSecretMutationSr25519SoftPathDeviceTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: AppDatabase
    private lateinit var preferences: InMemoryEncryptedPreferences
    private lateinit var coordinator: WalletSecretMutationCoordinator

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .build()
        preferences = InMemoryEncryptedPreferences()
        coordinator = WalletSecretMutationCoordinator(
            appDatabase = database,
            metaAccountDao = database.metaAccountDao(),
            journalStore = WalletSecretMutationJournalStore(preferences),
            encryptedPreferences = preferences
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun createBindsSoftPathSr25519WalletAndKeepsItsExactNonce() = runBlocking {
        SOFT_PATHS.forEachIndexed { variant, path ->
            val imported = importLikeRelease420(path, variant)
            val reimported = importLikeRelease420(path, variant)
            assertArrayEquals(path, imported.keypair.privateKey, reimported.keypair.privateKey)
            assertArrayEquals(path, imported.keypair.publicKey, reimported.keypair.publicKey)
            assertFalse(path, imported.keypair.nonce.contentEquals(reimported.keypair.nonce))

            val metaId = coordinator.create(
                prototype = prototype(imported.keypair),
                substrateSecretPlaintext = imported.encoded,
                ethereumSecretPlaintext = null,
                tonSecretPlaintext = null
            )

            assertNotNull(path, database.metaAccountDao().getMetaAccount(metaId))
            assertEquals(path, imported.encoded, preferences.value("$metaId:SUBSTRATE_SECRETS"))
        }
    }

    @Test
    fun createStillRejectsSoftPathSr25519RecordedWithAnotherJunction() = runBlocking {
        val imported = importLikeRelease420("//polkadot/0", variant = 7)
        val mismatched = SubstrateSecrets(
            substrateKeyPair = imported.keypair,
            entropy = imported.entropy,
            seed = imported.seed,
            substrateDerivationPath = "//polkadot/1"
        ).toHexString()

        try {
            coordinator.create(
                prototype = prototype(imported.keypair),
                substrateSecretPlaintext = mismatched,
                ethereumSecretPlaintext = null,
                tonSecretPlaintext = null
            )
            fail("A soft path that derives another scalar must not bind")
        } catch (failure: WalletSecretMutationCoordinatorException) {
            assertEquals(WalletMutationFailureReason.SECRET_BINDING_FAILED, failure.reason)
        }
        assertTrue(preferences.keys().isEmpty())
        assertTrue(database.metaAccountDao().getMetaAccounts().isEmpty())
    }

    /** Builds the secrets exactly like the 4.2.0 mnemonic import did. */
    private fun importLikeRelease420(path: String, variant: Int): ImportedWallet {
        val entropy = ByteArray(16) { (it * 7 + variant + 1).toByte() }
        val mnemonic = MnemonicCreator.fromEntropy(entropy.copyOf())
        val decodedPath = SubstrateJunctionDecoder.decode(path)
        val seed = SubstrateSeedFactory.deriveSeed32(
            mnemonicWords = mnemonic.words,
            password = decodedPath.password
        ).seed
        val keypair = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.SR25519,
            seed = seed,
            junctions = decodedPath.junctions
        ) as Sr25519Keypair
        return ImportedWallet(
            entropy = entropy,
            seed = seed,
            keypair = keypair,
            encoded = SubstrateSecrets(
                substrateKeyPair = keypair,
                entropy = entropy,
                seed = seed,
                substrateDerivationPath = path
            ).toHexString()
        )
    }

    private fun prototype(keypair: Sr25519Keypair) = MetaAccountLocal(
        substratePublicKey = keypair.publicKey,
        substrateCryptoType = CryptoType.SR25519,
        substrateAccountId = keypair.publicKey.substrateAccountId(),
        ethereumPublicKey = null,
        ethereumAddress = null,
        tonPublicKey = null,
        name = "Soft path wallet",
        isSelected = true,
        position = 0,
        isBackedUp = false,
        googleBackupAddress = null,
        initialized = false
    )

    private class ImportedWallet(
        val entropy: ByteArray,
        val seed: ByteArray,
        val keypair: Sr25519Keypair,
        val encoded: String
    )

    private class InMemoryEncryptedPreferences : EncryptedPreferences {

        private val values = linkedMapOf<String, String>()

        @Synchronized
        fun keys(): Set<String> = values.keys.toSet()

        @Synchronized
        fun value(key: String): String? = values[key]

        @Synchronized
        override fun putEncryptedString(field: String, value: String) {
            values[field] = value
        }

        @Synchronized
        override fun getDecryptedString(field: String): String? = values[field]

        @Synchronized
        override fun hasKey(field: String): Boolean = field in values

        @Synchronized
        override fun hasKeyWithPrefix(prefix: String): Boolean {
            return values.keys.any { it.startsWith(prefix) }
        }

        @Synchronized
        override fun keysWithPrefixes(
            prefixes: Set<String>,
            maxResultCount: Int,
            maxKeyBytes: Int,
            maxTotalKeyBytes: Int,
            failOnOversizedMatch: Boolean
        ): Set<String> {
            return values.keys
                .filter { key -> prefixes.any(key::startsWith) }
                .also { check(it.size <= maxResultCount) }
                .toSet()
        }

        @Synchronized
        override fun removeKey(field: String) {
            values.remove(field)
        }

        @Synchronized
        override fun replaceEncryptedStringsDurably(
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>
        ) {
            keysToRemove.forEach(values::remove)
            values.putAll(valuesToPut)
        }

        @Synchronized
        override fun quarantineEncryptedStringDurably(
            sourceKey: String,
            quarantineKey: String,
            expectedSnapshot: EncryptedPreferenceSnapshot
        ): Boolean {
            error("Binding a new wallet never quarantines")
        }

        override fun requireDurableStorageHealthy() = Unit
    }

    private companion object {
        val SOFT_PATHS = listOf("/0", "//polkadot/0", "//kusama//staking/1///p@ss")
    }
}

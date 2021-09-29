package jp.co.soramitsu.core_db.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.mappers.mapCryptoTypeToEncryption
import jp.co.soramitsu.common.data.mappers.mapEncryptionToCryptoType
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.chain.MetaAccountLocal
import jp.co.soramitsu.core_db.model.chain.MetaAccountLocal.Table.Column
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.test_shared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

private const val MNEMONIC_WORDS = "bottom drive obey lake curtain smoke basket hold race lonely fit walk"
private val MNEMONIC = MnemonicCreator.fromWords(MNEMONIC_WORDS)

private val SUBSTRATE_SEED = SubstrateSeedFactory.deriveSeed32(MNEMONIC_WORDS, password = null).seed
private val CRYPTO_TYPE = EncryptionType.SR25519
private val SUBSTRATE_KEYPAIR = SubstrateKeypairFactory.generate(CRYPTO_TYPE, seed = SUBSTRATE_SEED, junctions = emptyList())

private const val DERIVATION_PATH = "//test"

private val ETHEREUM_DERIVATION_PATH = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
private val DECODED_ETHEREUM_DERIVATION_PATH = BIP32JunctionDecoder.decode(ETHEREUM_DERIVATION_PATH)
private val ETHEREUM_SEED = EthereumSeedFactory.deriveSeed32(
    mnemonicWords = MNEMONIC_WORDS,
    password = DECODED_ETHEREUM_DERIVATION_PATH.password
).seed
private val ETHEREUM_KEYPAIR = EthereumKeypairFactory.generate(
    seed = ETHEREUM_SEED,
    junctions = DECODED_ETHEREUM_DERIVATION_PATH.junctions
)

private const val NAME = "TEST"

class V2MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    private val encryptedPreferences = HashMapEncryptedPreferences()
    private val storeV1 = SecretStoreV1Impl(encryptedPreferences)
    private val storeV2 = SecretStoreV2(encryptedPreferences)

    private val migration = V2Migration(storeV1, storeV2)

    @Test
    fun shouldMigrateWithMnemonic() = runBlocking {
        performSingleAccountTest(
            insertionType = Type.MNEMONIC,
            withEthereum = true,
            withEntropy = true,
            withSeed = true,
            withDerivationPath = true
        )
    }

    @Test
    fun shouldMigrateWithSeed() = runBlocking {
        performSingleAccountTest(
            insertionType = Type.SEED,
            withEthereum = false,
            withEntropy = false,
            withSeed = true,
            withDerivationPath = true
        )
    }

    @Test
    fun shouldMigrateWithCreate() = runBlocking {
        performSingleAccountTest(
            insertionType = Type.CREATE,
            withEthereum = true,
            withEntropy = true,
            withSeed = true,
            withDerivationPath = true
        )
    }

    @Test
    fun shouldMigrateWithJson() = runBlocking {
        performSingleAccountTest(
            insertionType = Type.JSON,
            withEthereum = false,
            withEntropy = false,
            withSeed = false,
            withDerivationPath = false
        )
    }

    @Test
    fun shouldMigrateWithKeypair() = runBlocking {
        performSingleAccountTest(
            insertionType = Type.KEYPAIR,
            withEthereum = false,
            withEntropy = false,
            withSeed = false,
            withDerivationPath = false
        )
    }

    private suspend fun performSingleAccountTest(
        insertionType: Type,
        withEntropy: Boolean,
        withEthereum: Boolean,
        withSeed: Boolean,
        withDerivationPath: Boolean
    ) {
        storeV1.insertSecrets(insertionType)

        val db = performMigration {
            insertAccount(publicKey = SUBSTRATE_KEYPAIR.publicKey, CRYPTO_TYPE)
        }

        val metaAccount = db.getMetaAccounts().firstOrNull() ?: error("There should be at least one account after migration")

        assertCorrectMetaAccount(metaAccount, withEthereum = withEthereum, selected = true)
        assertCorrectSecrets(
            metaAccountSecrets = storeV2.getMetaAccountSecrets(metaAccount.id),
            withEthereum = withEthereum,
            withEntropy = withEntropy,
            withSeed = withSeed,
            withDerivationPath = withDerivationPath
        )
    }

    private fun performMigration(oldDbBuilder: SupportSQLiteDatabase.() -> Unit) : SupportSQLiteDatabase {
        helper.createDatabase(TEST_DB, 26).apply {
            oldDbBuilder()

            close()
        }

        return helper.runMigrationsAndValidate(TEST_DB, 27, true, migration)
    }

    private fun assertCorrectMetaAccount(
        metaAccountLocal: MetaAccountLocal,
        selected: Boolean,
        withEthereum: Boolean,
    ) = with(metaAccountLocal) {
        assertEquals(NAME, name)

        assertArrayEquals(SUBSTRATE_KEYPAIR.publicKey, substratePublicKey)
        assertArrayEquals(SUBSTRATE_KEYPAIR.publicKey.substrateAccountId(), substrateAccountId)
        assertEquals(CRYPTO_TYPE, mapCryptoTypeToEncryption(substrateCryptoType))

        assertEquals(selected, metaAccountLocal.isSelected)

        if (withEthereum) {
            assertArrayEquals(ETHEREUM_KEYPAIR.publicKey, ethereumPublicKey)
            assertArrayEquals(ETHEREUM_KEYPAIR.publicKey.ethereumAddressFromPublicKey(), ethereumAddress)
        } else {
            assertNull(ethereumAddress)
            assertNull(ethereumPublicKey)
        }
    }

    private fun assertCorrectSecrets(
        metaAccountSecrets: EncodableStruct<MetaAccountSecrets>?,
        withEntropy: Boolean,
        withSeed: Boolean,
        withEthereum: Boolean,
        withDerivationPath: Boolean
    ) {
        requireNotNull(metaAccountSecrets)

        val expectedEntropy = MNEMONIC.entropy.takeIf { withEntropy }
        val expectedSeed = SUBSTRATE_SEED.takeIf { withSeed }
        val expectedEthereumKeypair = ETHEREUM_KEYPAIR.takeIf { withEthereum }
        val expectedEthereumDerivationPath = ETHEREUM_DERIVATION_PATH.takeIf { withEthereum }
        val expectedDerivationPath = DERIVATION_PATH.takeIf { withDerivationPath }

        assertArrayEquals(expectedEntropy, metaAccountSecrets[MetaAccountSecrets.Entropy])
        assertArrayEquals(expectedSeed, metaAccountSecrets[MetaAccountSecrets.Seed])

        assertCorrectKeypair(SUBSTRATE_KEYPAIR, metaAccountSecrets[MetaAccountSecrets.SubstrateKeypair])
        assertEquals(expectedDerivationPath, metaAccountSecrets[MetaAccountSecrets.SubstrateDerivationPath])

        assertCorrectKeypair(expectedEthereumKeypair, metaAccountSecrets[MetaAccountSecrets.EthereumKeypair])
        assertEquals(expectedEthereumDerivationPath, metaAccountSecrets[MetaAccountSecrets.EthereumDerivationPath])
    }

    private fun assertCorrectKeypair(
        expected: Keypair?,
        actual: EncodableStruct<KeyPairSchema>?
    ) {
        assertArrayEquals(expected?.publicKey, actual?.get(KeyPairSchema.PublicKey))
        assertArrayEquals(expected?.privateKey, actual?.get(KeyPairSchema.PrivateKey))
        assertArrayEquals((expected as? Sr25519Keypair)?.nonce, actual?.get(KeyPairSchema.Nonce))
    }

    private fun SupportSQLiteDatabase.getMetaAccounts(): List<MetaAccountLocal> {
        val cursor = query("SELECT * FROM ${MetaAccountLocal.Table.TABLE_NAME}")

        return cursor.map {
            val metaAccount = MetaAccountLocal(
                substratePublicKey= getBlob(getColumnIndex(Column.SUBSTRATE_PUBKEY)),
                substrateCryptoType = enumValueOf(getString(getColumnIndex(Column.SUBSTRATE_CRYPTO_TYPE))),
                substrateAccountId = getBlob(getColumnIndex(Column.SUBSTRATE_ACCOUNT_ID)),
                ethereumPublicKey = getBlob(getColumnIndex(Column.ETHEREUM_PUBKEY)),
                ethereumAddress = getBlob(getColumnIndex(Column.ETHEREUM_ADDRESS)),
                name = getString(getColumnIndex(Column.NAME)),
                isSelected = getInt(getColumnIndex(Column.IS_SELECTED)) == 1,
                position = getInt(getColumnIndex(Column.POSITION))
            )

            metaAccount.id = getLong(getColumnIndex(Column.ID))

            metaAccount
        }
    }

    private suspend fun SecretStoreV1.insertSecrets(type: Type) {
        val securitySource = when(type) {
            Type.MNEMONIC -> SecuritySource.Specified.Mnemonic(
                seed = SUBSTRATE_SEED,
                keypair = SUBSTRATE_KEYPAIR,
                mnemonic = MNEMONIC_WORDS,
                derivationPath = DERIVATION_PATH
            )
            Type.SEED -> SecuritySource.Specified.Seed(
                seed = SUBSTRATE_SEED,
                keypair = SUBSTRATE_KEYPAIR,
                derivationPath = DERIVATION_PATH
            )
            Type.KEYPAIR -> SecuritySource.Unspecified(
                keypair = SUBSTRATE_KEYPAIR
            )
            Type.JSON -> SecuritySource.Specified.Json(
                seed = null, // no seed for SR25519
                keypair = SUBSTRATE_KEYPAIR
            )
            Type.CREATE -> SecuritySource.Specified.Create(
                seed = SUBSTRATE_SEED,
                keypair = SUBSTRATE_KEYPAIR,
                mnemonic = MNEMONIC_WORDS,
                derivationPath = DERIVATION_PATH
            )
        }

        saveSecuritySource(SUBSTRATE_KEYPAIR.publicKey.toAddress(Node.NetworkType.POLKADOT), securitySource)
    }

    private fun SupportSQLiteDatabase.insertAccount(publicKey: ByteArray, encryptionType: EncryptionType) {
        val params = ContentValues().apply {
            put("address", publicKey.toAddress(Node.NetworkType.POLKADOT))
            put("publicKey", publicKey)
            put("cryptoType", mapEncryptionToCryptoType(encryptionType).ordinal)
            put("position", 0)
            put("networkType", Node.NetworkType.POLKADOT.ordinal)
            put("username", NAME)
        }

        insert("users", SQLiteDatabase.CONFLICT_REPLACE, params)
    }

    private enum class Type {
        MNEMONIC, SEED, JSON, KEYPAIR, CREATE
    }
}

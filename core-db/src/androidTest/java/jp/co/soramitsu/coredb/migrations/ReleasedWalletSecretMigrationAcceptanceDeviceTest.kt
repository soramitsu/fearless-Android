package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * A database-75 profile, the version Play 4.2.0 left on devices, holding one
 * wallet for every secret exactly as 4.2.0 (or 3.7.x for chain accounts)
 * stored it, opened twice through the production builder with the SR25519
 * library the app ships. The vectors are common's golden vectors from the
 * production-parity oracle; all key material is synthetic.
 *
 * The pre-K1 integrity pass quarantined 71 of these secrets (SR25519 soft
 * paths, whose nonce is random, and TON phrases typed with extra spaces,
 * capitals or a newline), which locked the whole app. Every one must open
 * with no quarantine or marker and keep its exact ciphertext.
 */
class ReleasedWalletSecretMigrationAcceptanceDeviceTest {

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
    fun version75ProfileKeepsEverySecretReleasedBuildsStoredAcrossTwoLaunches() {
        val vectors = loadVectors()
        assertEquals(259, vectors.size)
        assertEquals(71, vectors.count { it.getAsJsonObject("rc")["verdict"].asString == "reject" })
        val wallets = vectors.mapIndexed { index, vector -> ReleasedWallet(index + 1L, vector) }
        createVersion75Profile(wallets)
        val preferences = HashMapEncryptedPreferences()
        wallets.forEach { preferences.putEncryptedString(it.secretKey, it.stored) }

        assertEquals(APP_DATABASE_VERSION, openProductionDatabase(preferences))
        val afterFirstLaunch = wallets.associate { it.secretKey to preferences.getDecryptedString(it.secretKey) }
        assertEquals(APP_DATABASE_VERSION, openProductionDatabase(preferences))

        assertFalse(preferences.hasKeyWithPrefix(WalletSecretQuarantine.KEY_PREFIX))
        assertFalse(preferences.hasKeyWithPrefix(WalletPublicIdentityRecovery.KEY_PREFIX))
        wallets.forEach { wallet ->
            val kept = preferences.getDecryptedString(wallet.secretKey)
            assertEquals(wallet.case, afterFirstLaunch[wallet.secretKey], kept)
            val archived = preferences.getDecryptedString(WalletSecretArchive.keyFor(wallet.secretKey))
            if (wallet.sanitizerChangedFields.isEmpty()) {
                assertEquals(wallet.case, wallet.stored, kept)
                assertNull(wallet.case, archived)
            } else {
                // A raw private-key import stored the default path without
                // entropy; only that unbound path leaves the active payload,
                // and the value 4.2.0 stored is kept in the archive.
                assertEquals(wallet.case, wallet.stored, archived)
                assertEquals(wallet.case, listOf("EthereumDerivationPath"), wallet.sanitizerChangedFields)
                val original = EthereumSecrets.read(wallet.stored)
                val migrated = EthereumSecrets.read(checkNotNull(kept))
                assertNull(wallet.case, migrated[EthereumSecrets.EthereumDerivationPath])
                assertArrayEquals(
                    wallet.case,
                    original[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey],
                    migrated[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey]
                )
                assertArrayEquals(wallet.case, original[EthereumSecrets.Seed], migrated[EthereumSecrets.Seed])
            }
        }
    }

    private fun createVersion75Profile(wallets: List<ReleasedWallet>) {
        createdDatabases += DATABASE_NAME
        helper.createDatabase(DATABASE_NAME, 75).apply {
            CHAINS.forEach { (chainId, ecosystem) -> insertChain(chainId, ecosystem) }
            wallets.forEach { insertWallet(it) }
            close()
        }
    }

    private fun SupportSQLiteDatabase.insertChain(chainId: String, ecosystem: String) {
        val isEthereum = if (ecosystem == "ETHEREUM") 1 else 0
        execSQL(
            """
            INSERT INTO chains(
                id, name, icon, prefix, isEthereumBased, isTestNet,
                hasCrowdloans, supportStakingPool, isEthereumChain,
                isChainlinkProvider, supportNft, isUsesAppId, ecosystem
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                chainId, "Released $ecosystem chain", "fixture-icon", 42, isEthereum,
                0, 0, 0, isEthereum, 0, 0, 0, ecosystem
            )
        )
    }

    private fun SupportSQLiteDatabase.insertWallet(wallet: ReleasedWallet) {
        execSQL(
            """
            INSERT INTO meta_accounts(
                id, substratePublicKey, substrateCryptoType, substrateAccountId,
                ethereumPublicKey, ethereumAddress, tonPublicKey, name,
                isSelected, position, isBackedUp, googleBackupAddress, initialized
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                wallet.metaId,
                wallet.publicKey.takeIf { wallet.store == SUBSTRATE },
                wallet.cryptoType.takeIf { wallet.store == SUBSTRATE },
                wallet.accountId.takeIf { wallet.store == SUBSTRATE },
                wallet.publicKey.takeIf { wallet.store == ETHEREUM },
                wallet.address.takeIf { wallet.store == ETHEREUM },
                wallet.publicKey.takeIf { wallet.store == TON },
                wallet.case,
                if (wallet.metaId == 1L) 1 else 0,
                wallet.metaId - 1,
                1,
                null,
                1
            )
        )
        if (wallet.store != CHAIN_ACCOUNT) return
        execSQL(
            """
            INSERT INTO chain_accounts(
                metaId, chainId, publicKey, accountId, cryptoType, name, initialized
            ) VALUES(?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                wallet.metaId,
                if (wallet.isEthereumChainAccount) ETHEREUM_CHAIN_ID else SUBSTRATE_CHAIN_ID,
                wallet.publicKey,
                checkNotNull(wallet.accountId),
                checkNotNull(wallet.cryptoType),
                wallet.case,
                1
            )
        )
    }

    private fun openProductionDatabase(preferences: EncryptedPreferences): Int {
        var database: AppDatabase? = null
        try {
            database = AppDatabase.create(
                context = context,
                databaseName = DATABASE_NAME,
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

    private fun loadVectors(): List<JsonObject> {
        // core-db's androidTest assets include common/src/test/resources/wallet-secret-vectors.
        val stream = InstrumentationRegistry.getInstrumentation().context.assets
            .open("12-validator-acceptance.json")
        val root = InputStreamReader(stream, Charsets.UTF_8).use(JsonParser::parseReader).asJsonObject
        val vectors = root.getAsJsonArray("vectors").map { it.asJsonObject }
        check(vectors.size == root["count"].asInt) { "Vector count mismatch" }
        return vectors
    }

    private class ReleasedWallet(val metaId: Long, vector: JsonObject) {
        val case: String = vector["case"].asString
        val store: String = vector["store"].asString
        val stored: String = vector["stored"].asString
        val publicKey: ByteArray = vector["publicKey"].asString.fromHex()
        val accountId: ByteArray? = vector.optString("accountId")?.fromHex()
        val address: ByteArray? = vector.optString("address")?.fromHex()
        val isEthereumChainAccount = vector.optString("cryptoType") == "ECDSA(ethereum)"
        val cryptoType: String? = vector.optString("cryptoType")?.removeSuffix("(ethereum)")
        val sanitizerChangedFields: List<String> = vector.getAsJsonObject("rc")
            .getAsJsonArray("sanitizerChangedFields")
            ?.map { it.asString }
            .orEmpty()

        val secretKey: String = when (store) {
            SUBSTRATE -> "$metaId:SUBSTRATE_SECRETS"
            ETHEREUM -> "$metaId:ETHEREUM_SECRETS"
            TON -> "$metaId:TON_SECRETS"
            CHAIN_ACCOUNT -> "$metaId:${checkNotNull(accountId).toHexString()}:ACCESS_SECRETS"
            else -> error("Unknown store in $case")
        }

        private fun JsonObject.optString(name: String): String? =
            get(name)?.takeUnless { it.isJsonNull }?.asString
    }

    private companion object {
        const val DATABASE_NAME = "released-wallet-secret-acceptance-v75"
        const val SUBSTRATE = "SubstrateSecrets(V3)"
        const val ETHEREUM = "EthereumSecrets(V3)"
        const val TON = "TonSecrets(V3)"
        const val CHAIN_ACCOUNT = "ChainAccountSecrets(V2)"
        const val SUBSTRATE_CHAIN_ID = "released-substrate-chain"
        const val ETHEREUM_CHAIN_ID = "released-ethereum-chain"
        val CHAINS = listOf(SUBSTRATE_CHAIN_ID to "SUBSTRATE", ETHEREUM_CHAIN_ID to "ETHEREUM")
    }
}

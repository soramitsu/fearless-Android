package jp.co.soramitsu.account.impl.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * substrate-password-vectors.json (shared with web and iOS) on the native SR25519 library the app ships.
 * Host JVM tests have no SR25519, so only a device shows that each `///password` rule opens the account
 * the vectors name, and that the production import and the coordinator's re-derivation check save it.
 */
@RunWith(AndroidJUnit4::class)
class SubstratePasswordSr25519DeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyRuleDerivesTheVectorsSr25519Account() {
        for (vector in vectors()) {
            val path = SubstrateJunctionDecoder.decode(vector["derivationPath"].asString)
            for ((rule, key) in RULES) {
                val seed = SubstrateSeedFactory.deriveSeed32(vector["mnemonic"].asString, path.password, rule).seed
                val keypair = SubstrateKeypairFactory.generate(EncryptionType.SR25519, seed, path.junctions)
                assertEquals("${vector["id"].asString} $key", expectedSr25519(vector, key), keypair.publicKey.toHex())
            }
        }
    }

    @Test
    fun importWithEachRuleSavesTheVectorsSr25519Account() = runBlocking {
        for (vector in vectors()) {
            for ((rule, key) in RULES) {
                assertEquals("${vector["id"].asString} $key", expectedSr25519(vector, key), importSr25519(vector, rule))
            }
        }
    }

    private suspend fun importSr25519(vector: JsonObject, rule: SubstratePasswordRule): String {
        val preferences = HashMapEncryptedPreferences()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.metaAccountDao()
            val coordinator = WalletSecretMutationCoordinator(
                database, dao, WalletSecretMutationJournalStore(preferences), preferences
            )
            val id = SubstrateOrEvmAccountRepository(dao, coordinator).create(
                AddAccountPayload.SubstrateOrEvm(
                    accountName = "Imported ${vector["id"].asString}",
                    mnemonic = vector["mnemonic"].asString,
                    encryptionType = CryptoType.SR25519,
                    substrateDerivationPath = vector["derivationPath"].asString,
                    ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH,
                    googleBackupAddress = null,
                    isBackedUp = true,
                    substratePasswordRule = rule
                )
            )
            val wallet = requireNotNull(dao.getMetaAccount(id))
            val secrets = requireNotNull(
                SubstrateSecretStore(preferences).get(
                    id, wallet.substratePublicKey, wallet.substrateCryptoType, wallet.substrateAccountId
                )
            )
            val publicKey = requireNotNull(wallet.substratePublicKey)
            assertArrayEquals(publicKey, secrets[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PublicKey])
            assertArrayEquals(
                MnemonicCreator.fromWords(vector["mnemonic"].asString).entropy,
                secrets[SubstrateSecrets.Entropy]
            )
            assertEquals(vector["derivationPath"].asString, secrets[SubstrateSecrets.SubstrateDerivationPath])
            assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
            return publicKey.toHex()
        } finally {
            database.close()
        }
    }

    private fun expectedSr25519(vector: JsonObject, key: String): String =
        vector["accounts"].asJsonObject[key].asJsonObject["sr25519"].asJsonObject["publicKey"].asString

    private fun ByteArray.toHex() = "0x" + joinToString("") { "%02x".format(it) }

    private fun vectors(): List<JsonObject> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val document = assets.open("substrate-password-vectors.json").bufferedReader(Charsets.UTF_8).use {
            JsonParser.parseReader(it).asJsonObject
        }
        assertEquals("nfkd", document["canonicalRule"].asString)
        return document["vectors"].asJsonArray.map { it.asJsonObject }
    }

    private companion object {
        val RULES = listOf(
            SubstratePasswordRule.NFKD to "nfkd",
            SubstratePasswordRule.NFC to "nfc",
            SubstratePasswordRule.AS_TYPED to "asTyped"
        )
    }
}

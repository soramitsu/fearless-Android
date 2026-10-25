package jp.co.soramitsu.account.impl.domain.recovery

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonParser
import jp.co.soramitsu.account.impl.data.repository.WalletKeyIdentityReader
import jp.co.soramitsu.account.impl.data.repository.WalletSecretRebindService
import jp.co.soramitsu.common.data.secrets.ArchivedWalletMaterialDecoder
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Restores frozen outputs of shipped writers using the app's real SR25519 JNI. All keys are public fixtures. */
@RunWith(AndroidJUnit4::class)
class WalletRebindInstrumentedTest {
    @Test
    fun archivePreviewProvesEveryFrozenSr25519KeyIncludingJsonImports() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val vectors = context.assets.open("wallet-secret-vectors/12-validator-acceptance.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors").map { row -> row.asJsonObject }
        }.filter { it.get("cryptoType")?.asString == "SR25519" }
        assertEquals(104, vectors.size)
        val decoder = ArchivedWalletMaterialDecoder()
        for (vector in vectors) {
            val source = if (vector["store"].asString == "SubstrateSecrets(V3)") "7:SUBSTRATE_SECRETS"
                else "7:${vector["accountId"].asString.removePrefix("0x")}:ACCESS_SECRETS"
            val decoded = decoder.decode(source, vector["stored"].asString)
            val name = vector["case"].asString
            assertEquals(name, 0, decoded.unprovedParts)
            assertEquals(name, 1, decoded.candidates.size)
            assertEquals(name, vector["publicKey"].asString.removePrefix("0x"), Hex.toHexString(decoded.candidates.single().publicKey))
            assertEquals(name, vector["accountId"].asString.removePrefix("0x"), Hex.toHexString(decoded.candidates.single().accountId))
        }
    }

    @Test
    fun historicalSr25519RootAndNetworkKeysRebindAfterDamageIncludingSoftPaths() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val vectors = context.assets.open("wallet-secret-vectors/12-validator-acceptance.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors").map { row -> row.asJsonObject }
        }.filter { it.get("cryptoType")?.asString == "SR25519" && it.get("source")?.asString != "json" }
        assertEquals(102, vectors.size)
        val verifier = WalletIdentityVerifier(JsonSeedDecoder(Gson()))
        for (vector in vectors) {
            val publicKey = Hex.decode(vector.get("publicKey").asString.removePrefix("0x"))
            val accountId = Hex.decode(vector.get("accountId").asString.removePrefix("0x"))
            val stored = vector.get("stored").asString
            val path = vector.get("derivationPath")?.asString.orEmpty()
            val isRoot = vector.get("store").asString == "SubstrateSecrets(V3)"
            val identity = if (isRoot) {
                WalletKeyIdentity.Substrate(publicKey, accountId, CryptoType.SR25519)
            } else {
                WalletKeyIdentity.Chain(publicKey, accountId, CryptoType.SR25519)
            }
            val entropy = if (isRoot) SubstrateSecrets.read(stored)[SubstrateSecrets.Entropy]
                else ChainAccountSecrets.read(stored)[ChainAccountSecrets.Entropy]
            val seed = if (isRoot) SubstrateSecrets.read(stored)[SubstrateSecrets.Seed]
                else ChainAccountSecrets.read(stored)[ChainAccountSecrets.Seed]
            val candidate = if (entropy != null) {
                verifier.verifyMnemonic(identity, MnemonicCreator.fromEntropy(entropy).words, path)
            } else {
                verifier.verifySeed(identity, Hex.toHexString(checkNotNull(seed)), path)
            }
            val preferences = HashMapEncryptedPreferences()
            val key = if (isRoot) "1:SUBSTRATE_SECRETS" else "1:${Hex.toHexString(accountId)}:ACCESS_SECRETS"
            val quarantine = WalletSecretQuarantine.keyFor(key)
            preferences.putEncryptedString(quarantine, "damaged original bytes")
            val service = WalletSecretRebindService(
                preferences, WalletKeyIdentityReader { _, _ -> identity }, verifier,
                WalletSecretMutationJournalStore(preferences)
            )
            assertEquals(vector.get("case").asString, WalletSecretRebindService.Result.RESTORED, service.rebind(1, candidate))
            assertFalse(preferences.hasKey(quarantine))
            assertEquals("damaged original bytes", preferences.getDecryptedString(WalletSecretArchive.keyFor(key)))
            val restored = checkNotNull(preferences.getDecryptedString(key))
            // validate includes ownership and a fresh sign/verify proof with the native library.
            assertEquals(restored, verifier.validate(identity, restored))
        }
    }
}

package jp.co.soramitsu.account.impl.domain.recovery

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.SecureRandom
import jp.co.soramitsu.common.data.storage.envelope.WalletPinKdfParameters
import jp.co.soramitsu.common.data.storage.envelope.WalletRootKeyKind
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretKeyring
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretPayloads
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSealPolicy
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSealer
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.testshared.InMemoryKeyringDeviceFactor
import jp.co.soramitsu.testshared.InMemoryKeyringPreferences
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup wrap made from a stored root opens with every proof of that root: phrase, raw seed,
 * raw private key and JSON, across Substrate ed25519/ecdsa, Ethereum, TON, per-network accounts and
 * password ("universal") derivation paths. Vectors are the frozen historical library's; sr25519
 * needs the native library and is covered on a device.
 */
class WalletBackupWrapProofTest {
    private val verifier = WalletIdentityVerifier(JsonSeedDecoder(Gson()))
    private val golden = javaClass.classLoader!!.getResourceAsStream(
        "crypto-golden/shared-features-1.1.1.44-FLW.json"
    )!!.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }

    /** Stores [stored] as the root of [activeKey], wraps SK for it, then opens SK with each proof. */
    private fun assertEveryProofOpensTheWrap(activeKey: String, stored: VerifiedWalletSecret, proofs: List<VerifiedWalletSecret>) {
        val preferences = InMemoryKeyringPreferences()
        val keyring = WalletSecretKeyring(preferences, InMemoryKeyringDeviceFactor(), SecureRandom(), FAST_KDF)
        val created = keyring.create("123456")
        val target = checkNotNull(WalletSecretSealPolicy.classify(activeKey))
        WalletSecretSealer(preferences, keyring, SecureRandom(), true).ensureBackupWraps(target, stored.encoded, created)
        for (proof in proofs) {
            // Exactly what WalletPinRecovery does with a verified backup.
            val roots = WalletSecretPayloads.rootKeys(target.scope, proof.encoded)
            val opened = keyring.openWithBackup(roots.flatMap { root -> WalletRootKeyKind.entries.mapNotNull { it.canonical(root.privateKey) } })
            assertNotNull(activeKey, opened)
            assertArrayEquals(created.key, checkNotNull(opened).key)
        }
    }

    @Test
    fun `substrate ed25519 and ecdsa seed and phrase proofs, with password paths, reach one wrap`() {
        val vectors = golden.rows("keypairs").filter {
            it.text("kind") == "substrate" && it.text("cryptoType") in listOf("ED25519", "ECDSA") &&
                it.getAsJsonObject("expected").has("publicKey")
        }
        var phraseProofs = 0
        var passwordPaths = 0
        for (vector in vectors) {
            val publicKey = vector.getAsJsonObject("expected").bytes("publicKey")
            val identity = WalletKeyIdentity.Substrate(publicKey, publicKey.substrateAccountId(), CryptoType.valueOf(vector.text("cryptoType")))
            val stored = runCatching { verifier.verifySeed(identity, vector.text("seed"), vector.text("path")) }.getOrNull() ?: continue
            val phrase = runCatching { verifier.verifyMnemonic(identity, DEV_PHRASE, vector.text("path")) }.getOrNull()
            if (phrase != null) {
                phraseProofs++
                if (vector.text("path").contains("///")) passwordPaths++
            }
            assertEveryProofOpensTheWrap("7:SUBSTRATE_SECRETS", stored, listOfNotNull(stored, phrase))
            // A wallet imported from the phrase is proved by its exported raw seed too.
            if (phrase != null) assertEveryProofOpensTheWrap("7:SUBSTRATE_SECRETS", phrase, listOf(stored))
        }
        assertTrue("phrase proofs: $phraseProofs", phraseProofs >= 4)
    }

    @Test
    fun `ethereum phrase and raw private key proofs, with password paths, reach one wrap`() {
        val vectors = golden.rows("keypairs").filter { it.text("kind") == "ethereum" && it.getAsJsonObject("expected").has("publicKey") }
        var passwordPaths = 0
        for (vector in vectors) {
            val expected = vector.getAsJsonObject("expected")
            val publicKey = expected.bytes("publicKey")
            val identity = WalletKeyIdentity.Ethereum(publicKey, publicKey.ethereumAddressFromPublicKey())
            val phrase = verifier.verifyMnemonic(identity, vector.text("mnemonic"), vector.text("path"))
            val rawKey = verifier.verifySeed(identity, expected.text("privateKey"))
            if (vector.text("path").contains("///")) passwordPaths++
            assertEveryProofOpensTheWrap("7:ETHEREUM_SECRETS", phrase, listOf(phrase, rawKey))
            assertEveryProofOpensTheWrap("7:ETHEREUM_SECRETS", rawKey, listOf(phrase))
        }
        assertTrue(vectors.size >= 20)
        assertTrue("password paths: $passwordPaths", passwordPaths >= 2)
    }

    @Test
    fun `a per-network ethereum account is proved by its phrase`() {
        val vector = golden.rows("keypairs").first { it.text("kind") == "ethereum" }
        val publicKey = vector.getAsJsonObject("expected").bytes("publicKey")
        val address = publicKey.ethereumAddressFromPublicKey()
        val identity = WalletKeyIdentity.Chain(publicKey, address, CryptoType.ECDSA)
        val phrase = verifier.verifyMnemonic(identity, vector.text("mnemonic"), vector.text("path"))
        assertEveryProofOpensTheWrap("7:${Hex.toHexString(address)}:ACCESS_SECRETS", phrase, listOf(phrase))
    }

    @Test
    fun `json backups reach the wrap of the same account imported from its seed or key`() {
        val rows = golden.rows("json_keyring").filter { it.getAsJsonObject("expected").has("keypair") }
        assertTrue(rows.size >= 3)
        for (row in rows) {
            val expected = row.getAsJsonObject("expected")
            val keypair = expected.getAsJsonObject("keypair")
            val publicKey = keypair.bytes("publicKey")
            when (val encryption = expected.text("encryption")) {
                "ED25519", "ECDSA" -> {
                    val identity = WalletKeyIdentity.Substrate(publicKey, publicKey.substrateAccountId(), CryptoType.valueOf(encryption))
                    val json = verifier.verifyJson(identity, row.text("json"), row.text("password"))
                    val seed = verifier.verifySeed(identity, expected.text("seed"))
                    assertEveryProofOpensTheWrap("7:SUBSTRATE_SECRETS", seed, listOf(json))
                    assertEveryProofOpensTheWrap("7:SUBSTRATE_SECRETS", json, listOf(seed))
                }
                else -> {
                    val identity = WalletKeyIdentity.Ethereum(publicKey, publicKey.ethereumAddressFromPublicKey())
                    val json = verifier.verifyJson(identity, row.text("json"), row.text("password"))
                    val rawKey = verifier.verifySeed(identity, keypair.text("privateKey"))
                    assertEveryProofOpensTheWrap("7:ETHEREUM_SECRETS", rawKey, listOf(json))
                }
            }
        }
    }

    @Test
    fun `every historical TON typed variant reaches the wrap of its stored phrase`() {
        for (vector in golden.rows("ton")) {
            val identity = WalletKeyIdentity.Ton(vector.bytes("publicKey"))
            val stored = verifier.verifyMnemonic(identity, vector.text("typedText"))
            // Another typing that derives the same key stores other words, but the same key.
            val retyped = runCatching { verifier.verifyMnemonic(identity, vector.text("typedText").trim().lowercase()) }.getOrNull()
            assertEveryProofOpensTheWrap("7:TON_SECRETS", stored, listOfNotNull(stored, retyped))
        }
    }

    private fun JsonObject.rows(key: String) = getAsJsonArray(key).map { it.asJsonObject }
    private fun JsonObject.text(key: String) = get(key).asString
    private fun JsonObject.bytes(key: String) = Hex.decode(text(key).removePrefix("0x"))

    private companion object {
        const val DEV_PHRASE = "bottom drive obey lake curtain smoke basket hold race lonely fit walk"
        val FAST_KDF = WalletPinKdfParameters(memoryKiB = 64, iterations = 1, parallelism = 1)
    }
}

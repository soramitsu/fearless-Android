package jp.co.soramitsu.account.impl.domain.recovery

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import java.security.SecureRandom
import jp.co.soramitsu.common.data.storage.envelope.WalletPinKdfParameters
import jp.co.soramitsu.common.data.storage.envelope.WalletRootKeyKind
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretKeyring
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretPayloads
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSealPolicy
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSealer
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.testshared.InMemoryKeyringHardwareKey
import jp.co.soramitsu.testshared.InMemoryKeyringPreferences
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * sr25519 needs the shipped native library, so this runs on a device: a backup wrap made from the
 * stored 64-byte expanded key opens with the phrase, the raw mini-secret seed and a JSON export of
 * the same account, with and without a `///password` path.
 */
@RunWith(AndroidJUnit4::class)
class WalletBackupWrapSr25519DeviceTest {
    private val verifier = WalletIdentityVerifier(JsonSeedDecoder(Gson()))

    @Test
    fun everySr25519ProofReachesTheWrapOfItsStoredKey() {
        for (path in listOf("", "//polkadot", "//hard/soft", "//hard/soft///pw")) {
            val decoded = path.takeIf { it.isNotEmpty() }?.let(SubstrateJunctionDecoder::decode)
            val seed = SubstrateSeedFactory.deriveSeed32(PHRASE, decoded?.password, SubstratePasswordRule.NFKD).seed
            val keypair = SubstrateKeypairFactory.generate(EncryptionType.SR25519, seed, decoded?.junctions.orEmpty())
            val identity = WalletKeyIdentity.Substrate(keypair.publicKey, keypair.publicKey.substrateAccountId(), CryptoType.SR25519)

            val phrase = verifier.verifyMnemonic(identity, PHRASE, path)
            // Raw-seed imports apply the junctions only; the password already shaped this seed.
            val junctionsOnly = path.substringBefore("///")
            val rawSeed = verifier.verifySeed(identity, Hex.toHexString(seed), junctionsOnly)
            val json = JsonSeedEncoder(Gson()).generate(
                keypair = keypair,
                seed = seed.takeIf { path.isEmpty() },
                password = "json-password",
                name = "synthetic",
                multiChainEncryption = MultiChainEncryption.Substrate(EncryptionType.SR25519),
                genesisHash = GENESIS,
                address = keypair.publicKey.toAddress(42)
            )
            val fromJson = verifier.verifyJson(identity, json, "json-password")

            for (stored in listOf(phrase, rawSeed, fromJson)) {
                assertEveryProofOpensTheWrap(stored, listOf(phrase, rawSeed, fromJson))
            }
        }
    }

    private fun assertEveryProofOpensTheWrap(stored: VerifiedWalletSecret, proofs: List<VerifiedWalletSecret>) {
        val preferences = InMemoryKeyringPreferences()
        val keyring = WalletSecretKeyring(preferences, InMemoryKeyringHardwareKey(), SecureRandom(), FAST_KDF)
        val created = keyring.create("123456")
        val target = checkNotNull(WalletSecretSealPolicy.classify("7:SUBSTRATE_SECRETS"))
        WalletSecretSealer(preferences, keyring, SecureRandom(), true).ensureBackupWraps(target, stored.encoded, created)
        for (proof in proofs) {
            val roots = WalletSecretPayloads.rootKeys(target.scope, proof.encoded)
            val opened = keyring.openWithBackup(roots.flatMap { root -> WalletRootKeyKind.entries.mapNotNull { it.canonical(root.privateKey) } })
            assertNotNull(opened)
            assertArrayEquals(created.key, checkNotNull(opened).key)
        }
    }

    private companion object {
        const val PHRASE = "bottom drive obey lake curtain smoke basket hold race lonely fit walk"
        const val GENESIS = "0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        val FAST_KDF = WalletPinKdfParameters(memoryKiB = 64, iterations = 1, parallelism = 1)
    }
}

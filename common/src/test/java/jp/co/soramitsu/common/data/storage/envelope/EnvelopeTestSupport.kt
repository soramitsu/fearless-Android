package jp.co.soramitsu.common.data.storage.envelope

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.storage.encrypt.WalletPinHealth
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.scale.toHexString

/** An in-memory K_hw. [lose] simulates a Keystore entry that is gone. */
internal class FakeKeyringHardwareKey : WalletKeyringHardwareKey {
    private val keys = mutableMapOf<String, ByteArray>()
    private var next = 0
    var creations = 0
        private set

    override fun create(): String {
        creations++
        val alias = "test_hw_${next++}"
        keys[alias] = ByteArray(32).also(SecureRandom()::nextBytes)
        return alias
    }

    override fun hmac(alias: String, data: ByteArray): ByteArray {
        val key = keys[alias] ?: throw WalletKeyringHardwareKeyLostException("missing")
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
    }

    override fun exists(alias: String): Boolean = alias in keys

    fun loseAll() = keys.clear()
}

/** Cheap Argon2id parameters so unit tests stay fast; production uses [WalletPinKdfParameters.DEFAULT]. */
internal val TEST_KDF = WalletPinKdfParameters(memoryKiB = 64, iterations = 1, parallelism = 1)

internal fun resetEnvelopeGlobals() {
    WalletSecretSession.clear()
    WalletSessionLock.lock()
    WalletPinHealth.replace(null)
    WalletPinHealth.replaceKeyring(null)
}

/** Synthetic test vectors only: deterministic bytes, never real keys. */
internal object SyntheticSecrets {
    fun bytes(seed: Int, size: Int = 32) = ByteArray(size) { ((it * 31 + seed * 7 + 11) and 0x7f).toByte() }

    fun substrateSr25519(seed: Int): String = SubstrateSecrets(
        substrateKeyPair = Sr25519Keypair(privateKey = bytes(seed), publicKey = bytes(seed + 100), nonce = bytes(seed + 200)),
        entropy = bytes(seed + 300, 16),
        seed = bytes(seed + 400),
        substrateDerivationPath = "//test"
    ).toHexString()

    fun ethereum(seed: Int): String = EthereumSecrets(
        entropy = bytes(seed + 300, 16),
        seed = bytes(seed + 400, 64),
        ethereumKeypair = Keypair(publicKey = bytes(seed + 100, 64), privateKey = bytes(seed)),
        ethereumDerivationPath = "m/44'/60'/0'/0/0"
    ).toHexString()

    fun ton(seed: Int): String = TonSecrets(
        seed = "synthetic test words $seed".encodeToByteArray(),
        tonKeypair = Keypair(publicKey = bytes(seed + 100), privateKey = bytes(seed))
    ).toHexString()

    fun chainAccount(seed: Int): String = ChainAccountSecrets(
        keyPair = Keypair(publicKey = bytes(seed + 100, 33), privateKey = bytes(seed)),
        entropy = null,
        seed = bytes(seed + 400),
        derivationPath = null
    ).toHexString()
}

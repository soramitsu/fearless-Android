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

/** A plain in-memory SharedPreferences whose commits are atomic, like the platform's. */
internal class MapSharedPreferences(
    val values: MutableMap<String, Any?> = linkedMapOf()
) : android.content.SharedPreferences {
    /** Simulated process death: after this many commits nothing reaches storage any more. */
    var commitsBeforeDeath: Int? = null
    /** Whether the fatal commit itself still reached storage before the process died. */
    var fatalCommitApplied = true
    var commits = 0
        private set
    val dead: Boolean get() = commitsBeforeDeath?.let { commits > it } == true

    override fun getAll(): MutableMap<String, *> = LinkedHashMap(values)
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = key in values
    override fun edit(): android.content.SharedPreferences.Editor = MapEditor()
    override fun registerOnSharedPreferenceChangeListener(
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class MapEditor : android.content.SharedPreferences.Editor {
        private val puts = linkedMapOf<String, Any?>()
        private val removes = linkedSetOf<String>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { puts[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { puts[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { puts[key] = value }
        override fun putLong(key: String, value: Long) = apply { puts[key] = value }
        override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
        override fun remove(key: String) = apply { removes += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (dead) throw SimulatedProcessDeath()
            commits++
            val fatal = dead
            if (!fatal || fatalCommitApplied) {
                if (clear) values.clear()
                removes.forEach(values::remove)
                puts.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            }
            if (fatal) throw SimulatedProcessDeath()
            return true
        }
        override fun apply() {
            commit()
        }
    }
}

/**
 * Production [jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl] and
 * keyring over one in-memory preference file. The device-key layer is a reversible test
 * transform ("v2:test:" + plaintext) so tests can see the inner value exactly as stored.
 */
internal class SimulatedProcessDeath : RuntimeException("Simulated process death")

internal class EnvelopeStorageHarness(
    compiledSealing: Boolean = true,
    val shared: MapSharedPreferences = MapSharedPreferences(),
    /** The Keystore survives a process restart. */
    val hardware: FakeKeyringHardwareKey = FakeKeyringHardwareKey()
) {
    val preferences = jp.co.soramitsu.common.data.storage.PreferencesImpl(shared)
    val crypto: jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil =
        org.mockito.kotlin.mock()
    val keyring = WalletSecretKeyring(preferences, hardware, SecureRandom(), TEST_KDF)
    val sealer = WalletSecretSealer(preferences, keyring, SecureRandom(), compiledSealing)
    val storage = jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl(preferences, crypto, sealer)
    val authority = WalletPinAuthority(storage, keyring, sealer)
    val journal = jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore(storage)
    val migration = WalletSecretSealingMigration(storage, keyring, sealer, authority, journal)

    init {
        org.mockito.kotlin.whenever(crypto.encrypt(org.mockito.kotlin.any())).thenAnswer { "v2:test:" + it.getArgument<String>(0) }
        org.mockito.kotlin.whenever(crypto.isModernCiphertext(org.mockito.kotlin.anyOrNull())).thenAnswer {
            it.getArgument<String?>(0)?.startsWith("v2:test:") == true
        }
        org.mockito.kotlin.whenever(crypto.decrypt(org.mockito.kotlin.any())).thenAnswer {
            it.getArgument<String>(0).takeIf { value -> value.startsWith("v2:test:") }?.removePrefix("v2:test:").orEmpty()
        }
        org.mockito.kotlin.whenever(crypto.assessPinHealth()).thenAnswer {
            val snapshot = preferences.pinPreferenceSnapshot()
            jp.co.soramitsu.common.data.storage.encrypt.PinHealth.assess(snapshot.exists, snapshot.ciphertext?.let(crypto::decrypt))
        }
    }

    /** The plaintext the device-key layer holds for [key]: the envelope or the legacy value. */
    fun inner(key: String): String? = (shared.values[key] as? String)?.removePrefix("v2:test:")

    /** Stores [plaintext] as an older build did, without any envelope. */
    fun putLegacy(key: String, plaintext: String) {
        shared.values[key] = "v2:test:$plaintext"
    }
}

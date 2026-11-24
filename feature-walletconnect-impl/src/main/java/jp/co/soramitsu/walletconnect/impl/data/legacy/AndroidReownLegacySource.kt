package jp.co.soramitsu.walletconnect.impl.data.legacy

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Reads what the removed Reown SDK (1.6.9, and the WalletConnect SDK before the rename) left in the app's
 * private storage, exactly where and how it wrote it:
 * - `databases/WalletConnectV2.db` and `databases/WalletConnectAndroidCore.db`, SQLCipher 4;
 * - `shared_prefs/wc_key_store.xml`: EncryptedSharedPreferences (AES256-SIV keys, AES256-GCM values) under
 *   the Keystore AES-GCM key `wc_keystore_key`, holding the WalletConnect key chain;
 * - `shared_prefs/db_key_store.xml`: the same scheme under `_wc_db_key_`, whose `encryptedDBKey` is the
 *   databases' random 32-byte passphrase, AES-GCM encrypted with that same Keystore key and stored as
 *   base64 of [IV length (4 bytes, big-endian)][IV][ciphertext and tag].
 *
 * Nothing here writes: files are read as bytes, and the preferences are opened only when their Keystore
 * key and both Tink keysets already exist, because opening an incomplete file would write new keysets into
 * it and Tink would create a missing Keystore key.
 */
internal class AndroidReownLegacySource(
    private val context: Context,
    private val keyStore: () -> KeyStore = { KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) } }
) : ReownLegacySource {

    override fun hasLegacyData(): Boolean =
        DATABASES.any { context.getDatabasePath(it).exists() } || PREFERENCES.any { preferencesFile(it).exists() }

    override fun readKeyChain(): Map<String, String> =
        encryptedPreferences(KEY_CHAIN_PREFERENCES, KEY_CHAIN_ALIAS)?.all.orEmpty()
            .mapNotNull { (tag, value) -> (value as? String)?.let { tag to it } }
            .toMap()

    override fun readDatabasePassphrase(): ByteArray? {
        val stored = encryptedPreferences(DATABASE_KEY_PREFERENCES, DATABASE_KEY_ALIAS)?.getString(ENCRYPTED_DATABASE_KEY, null)
        val key = (keyStore().getEntry(DATABASE_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (stored == null || key == null) return null
        return unwrapDatabasePassphrase(Base64.decode(stored, Base64.DEFAULT)) { iv, ciphertext -> decryptGcm(key, iv, ciphertext) }
    }

    override fun readDatabase(name: String): LegacyDatabaseFiles? {
        val file = context.getDatabasePath(name)
        if (!file.isFile) return null
        return LegacyDatabaseFiles(
            database = file.readBytes(),
            journal = File(file.path + JOURNAL_SUFFIX).takeIf { it.isFile }?.readBytes(),
            wal = File(file.path + WAL_SUFFIX).takeIf { it.isFile }?.readBytes()
        )
    }

    private fun encryptedPreferences(name: String, alias: String): SharedPreferences? {
        if (!preferencesFile(name).isFile) return null
        val plain = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val complete = plain.contains(TINK_KEY_KEYSET) && plain.contains(TINK_VALUE_KEYSET) && keyStore().containsAlias(alias)
        if (!complete) return null
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_BITS)
            .build()
        return EncryptedSharedPreferences.create(
            name,
            MasterKeys.getOrCreate(spec),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun preferencesFile(name: String) =
        File(File(context.applicationInfo.dataDir, SHARED_PREFS_DIRECTORY), "$name.xml")

    private fun decryptGcm(
        key: SecretKey,
        iv: ByteArray,
        ciphertext: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        const val KEY_CHAIN_PREFERENCES = "wc_key_store"
        const val KEY_CHAIN_ALIAS = "wc_keystore_key"
        const val DATABASE_KEY_PREFERENCES = "db_key_store"
        const val DATABASE_KEY_ALIAS = "_wc_db_key_"
        private const val ENCRYPTED_DATABASE_KEY = "encryptedDBKey"
        private const val TINK_KEY_KEYSET = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        private const val TINK_VALUE_KEYSET = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val SHARED_PREFS_DIRECTORY = "shared_prefs"
        private const val JOURNAL_SUFFIX = "-journal"
        private const val WAL_SUFFIX = "-wal"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private val DATABASES = listOf(ReownLegacySnapshotReader.SIGN_DATABASE, ReownLegacySnapshotReader.CORE_DATABASE)
        private val PREFERENCES = listOf(KEY_CHAIN_PREFERENCES, DATABASE_KEY_PREFERENCES)
    }
}

/**
 * Opens the SDK's wrapped database passphrase: [IV length (4 bytes, big-endian)][IV][AES-GCM ciphertext
 * and tag], decrypted by [decrypt]. Throws [LegacyDatabaseException] (without any of the bytes) when the
 * layout is wrong.
 */
internal fun unwrapDatabasePassphrase(
    wrapped: ByteArray,
    decrypt: (iv: ByteArray, ciphertext: ByteArray) -> ByteArray
): ByteArray {
    val ivLength = if (wrapped.size >= Int.SIZE_BYTES) wrapped.int32(0) else -1
    val ciphertextStart = Int.SIZE_BYTES + ivLength
    if (ivLength !in MIN_GCM_IV_BYTES..MAX_GCM_IV_BYTES || ciphertextStart >= wrapped.size) {
        throw LegacyDatabaseException("The stored database key has an unexpected layout")
    }
    return decrypt(wrapped.copyOfRange(Int.SIZE_BYTES, ciphertextStart), wrapped.copyOfRange(ciphertextStart, wrapped.size))
}

private const val MIN_GCM_IV_BYTES = 12
private const val MAX_GCM_IV_BYTES = 16

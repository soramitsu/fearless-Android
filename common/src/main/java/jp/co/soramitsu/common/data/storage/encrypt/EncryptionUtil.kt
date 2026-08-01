package jp.co.soramitsu.common.data.storage.encrypt

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import java.security.spec.AlgorithmParameterSpec
import java.util.Calendar
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.security.auth.x500.X500Principal
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import org.bouncycastle.util.Arrays
import org.bouncycastle.util.encoders.Base64

enum class WalletSecureStorageFailureKind {
    RETRYABLE,
    PROCESS_RESTART_REQUIRED,
    PERMANENT_KEY_LOSS
}

class WalletSecureStorageUnavailableException(
    message: String,
    cause: Throwable? = null,
    val kind: WalletSecureStorageFailureKind =
        WalletSecureStorageFailureKind.RETRYABLE
) : IllegalStateException(message, cause)

internal object PreferenceAesKeyPolicy {

    fun readStoredWrappedKey(read: () -> String?): String? {
        return try {
            read()
        } catch (failure: ClassCastException) {
            throw WalletSecureStorageUnavailableException(
                "The wrapped wallet encryption key has an invalid stored type",
                failure,
                WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS
            )
        }
    }

    fun decodeStoredWrappedKey(
        encoded: String,
        decode: (String) -> ByteArray
    ): ByteArray {
        val decoded = try {
            decode(encoded)
        } catch (failure: Exception) {
            throw WalletSecureStorageUnavailableException(
                "The wrapped wallet encryption key is not valid Base64",
                failure,
                WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS
            )
        }
        if (decoded.isEmpty()) {
            throw WalletSecureStorageUnavailableException(
                "The wrapped wallet encryption key is empty",
                kind = WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS
            )
        }
        return decoded
    }

    fun requireExistingWrappingKey(
        allowCreate: Boolean,
        hasPrivateKey: Boolean,
        hasPublicKey: Boolean
    ) {
        if (!allowCreate && (!hasPrivateKey || !hasPublicKey)) {
            throw WalletSecureStorageUnavailableException(
                "The existing wallet wrapping key is unavailable",
                kind = WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS
            )
        }
    }

    fun mayCreateNewKey(
        wrappedKey: String?,
        preferenceKeys: Set<String>,
        hasExistingWalletRecords: Boolean
    ): Boolean {
        if (!wrappedKey.isNullOrEmpty()) return false

        return !hasExistingWalletRecords &&
            preferenceKeys.none(WalletMasterKeyAttestation::isProtectedPayloadKey)
    }

    fun requireValidExistingKey(unwrappedKey: ByteArray?): ByteArray {
        if (unwrappedKey == null || unwrappedKey.size !in setOf(16, 24, 32)) {
            throw WalletSecureStorageUnavailableException(
                "The existing wallet encryption key is unavailable",
                kind = WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS
            )
        }

        return unwrappedKey
    }

    fun classifyExistingWrappedKeyFailure(
        failure: Throwable
    ): WalletSecureStorageFailureKind {
        var current: Throwable? = failure
        val visited = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Throwable, Boolean>()
        )

        repeat(MAX_KEY_FAILURE_CAUSE_DEPTH) {
            val candidate = current
                ?: return WalletSecureStorageFailureKind.RETRYABLE
            if (!visited.add(candidate)) {
                return WalletSecureStorageFailureKind.RETRYABLE
            }
            if (
                candidate is BadPaddingException ||
                candidate is IllegalBlockSizeException ||
                candidate is InvalidKeyException ||
                candidate is KeyPermanentlyInvalidatedException ||
                candidate is UnrecoverableKeyException
            ) {
                return WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS
            }
            current = try {
                candidate.cause
            } catch (_: Throwable) {
                return WalletSecureStorageFailureKind.RETRYABLE
            }
        }

        return WalletSecureStorageFailureKind.RETRYABLE
    }

    private const val MAX_KEY_FAILURE_CAUSE_DEPTH = 32
}

internal fun <T> loadWalletKeyMaterialWithRetry(
    load: () -> T
): T {
    var lastFailure: Exception? = null
    repeat(KEYSTORE_LOAD_ATTEMPTS) {
        try {
            return load()
        } catch (failure: Exception) {
            lastFailure = failure
        }
    }

    throw checkNotNull(lastFailure)
}

private data class WalletKeyMaterial(
    val privateKey: PrivateKey,
    val publicKey: PublicKey
)

internal fun interface WalletPayloadDecryptor {

    @Throws(Exception::class)
    fun decrypt(
        transformation: String,
        key: ByteArray,
        parameters: AlgorithmParameterSpec,
        ciphertext: ByteArray,
        secureRandom: SecureRandom
    ): ByteArray
}

internal object JcaWalletPayloadDecryptor : WalletPayloadDecryptor {

    override fun decrypt(
        transformation: String,
        key: ByteArray,
        parameters: AlgorithmParameterSpec,
        ciphertext: ByteArray,
        secureRandom: SecureRandom
    ): ByteArray {
        return Cipher.getInstance(transformation).run {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                parameters,
                secureRandom
            )
            doFinal(ciphertext)
        }
    }
}

private class WalletPayloadCorruptionException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

private const val KEYSTORE_LOAD_ATTEMPTS = 2

class EncryptionUtil internal constructor(
    private val context: Context,
    private val payloadDecryptor: WalletPayloadDecryptor
) {

    @Inject
    constructor(context: Context) : this(
        context = context,
        payloadDecryptor = JcaWalletPayloadDecryptor
    )

    @Volatile
    private var privateKey: PrivateKey? = null

    @Volatile
    private var publicKey: PublicKey? = null

    companion object {
        private const val RSA = "RSA"
        private const val AES = "AES"
        private const val KEY_STORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        private const val KEY_ALIAS = "key_alias"
        private const val LEGACY_BLOCK_SIZE = 16
        private const val AES_KEY_LENGTH = 256
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_SIZE_BITS = 128
        private const val GCM_TAG_SIZE_BYTES = GCM_TAG_SIZE_BITS / 8
        private const val MODERN_CIPHER_PREFIX = "v2:"
        private const val MAX_ENCRYPTED_CIPHERTEXT_CHARS = 2_097_152
        private const val MAX_WRAPPED_KEY_CHARS = 4_096
        private const val MAX_ATTESTATION_PLAINTEXT_CHARS = 1_048_576
        private const val APP_DATABASE_NAME = "app.db"
        private val WALLET_ACCOUNT_TABLES = listOf("users", "meta_accounts")

        private const val SECRET_KEY = "secret_key"
        private val secureRandom = SecureRandom()
        private val walletKeyLock = Any()
    }

    fun getPrerenceAesKey(): Key = synchronized(walletKeyLock) {
        WalletSecureStorageHealth.requireHealthy()

        return try {
            getPreferenceAesKeyInternal()
        } catch (failure: WalletSecureStorageUnavailableException) {
            if (
                failure.kind ==
                WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED
            ) {
                WalletSecureStorageHealth.latchProcessRestartRequired()
            }
            throw failure
        } catch (failure: Exception) {
            // Every failure involving the single device-wide wrapped key is a
            // global secure-storage failure. Migration callers must never
            // misclassify malformed Base64, a wrong preference value type, or
            // a provider error as corruption in one wallet and quarantine it.
            throw WalletSecureStorageUnavailableException(
                "The wallet encryption key is unavailable",
                failure,
                PreferenceAesKeyPolicy.classifyExistingWrappedKeyFailure(
                    failure
                )
            )
        }
    }

    private fun getPreferenceAesKeyInternal(): Key {
        val prefs = context.getSharedPreferences(KEY_ALIAS, Context.MODE_PRIVATE)
        val encryptedKey = PreferenceAesKeyPolicy.readStoredWrappedKey {
            prefs.getString(SECRET_KEY, "")
        }

        if (encryptedKey.isNullOrEmpty()) {
            val walletPreferenceKeys = context
                .getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)
                .all
                .keys

            if (
                !PreferenceAesKeyPolicy.mayCreateNewKey(
                    wrappedKey = encryptedKey,
                    preferenceKeys = walletPreferenceKeys,
                    hasExistingWalletRecords = hasExistingWalletRecords()
                )
            ) {
                throw WalletSecureStorageUnavailableException(
                    "The wallet encryption key is missing while encrypted data still exists",
                    kind = WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS
                )
            }

            ensureKeystoreReady(allowCreate = true)
            val keyGenerator = KeyGenerator.getInstance(AES)
            keyGenerator.init(AES_KEY_LENGTH, secureRandom)
            val secretKey = keyGenerator.generateKey()
            val wrappedKey = encryptRsa(secretKey.encoded)
            if (wrappedKey.isEmpty()) {
                throw WalletSecureStorageUnavailableException(
                    "Unable to wrap a new wallet encryption key"
                )
            }

            persistWrappedKeyDurably(prefs, wrappedKey)

            attestPreferenceAesKey(secretKey)
            return secretKey
        }

        if (encryptedKey.length > MAX_WRAPPED_KEY_CHARS) {
            throw WalletSecureStorageUnavailableException(
                "The wrapped wallet encryption key exceeds the safe decode limit",
                kind = WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS
            )
        }
        ensureKeystoreReady(allowCreate = false)
        val key = try {
            PreferenceAesKeyPolicy.requireValidExistingKey(
                decryptExistingWrappedKey(encryptedKey)
            )
        } catch (failure: Exception) {
            clearLoadedKeystore()
            throw failure
        }

        return SecretKeySpec(key, AES).also { secretKey ->
            attestPreferenceAesKey(secretKey)
        }
    }

    private fun attestPreferenceAesKey(key: Key) {
        val walletPreferences = context.getSharedPreferences(
            SHARED_PREFERENCES_FILE,
            Context.MODE_PRIVATE
        )
        val allPreferences = walletPreferences.all
        val hasSentinelEntry = allPreferences.containsKey(
            WalletMasterKeyAttestation.SENTINEL_KEY
        )
        val sentinelCiphertext = allPreferences[WalletMasterKeyAttestation.SENTINEL_KEY]
            as? String
        val sentinelIsValid = sentinelCiphertext?.let { ciphertext ->
            try {
                decryptStrict(key.encoded, ciphertext) ==
                    WalletMasterKeyAttestation.SENTINEL_PLAINTEXT
            } catch (failure: WalletPayloadCorruptionException) {
                false
            }
        } == true

        // PIN is the global unlock boundary. Validate it before repairing or
        // creating any sentinel so a failed startup remains side-effect free.
        requireExistingPinHealthy(allPreferences, key.encoded)

        if (!sentinelIsValid) {
            val candidates = allPreferences
                .filterKeys(WalletMasterKeyAttestation::isAuthenticationCandidateKey)
            val authenticatedByExistingPayload = candidates.any { (field, storedValue) ->
                val ciphertext = storedValue as? String ?: return@any false
                try {
                    val plaintext = decryptStrict(key.encoded, ciphertext)
                    if (isModernCiphertext(ciphertext)) {
                        plaintext.isNotEmpty() &&
                            plaintext.length <= MAX_ATTESTATION_PLAINTEXT_CHARS
                    } else {
                        WalletMasterKeyAttestation.semanticValidationKey(field)?.let {
                            WalletMasterKeyAttestation.isSemanticallyValidLegacyCandidate(
                                key = it,
                                plaintext = plaintext
                            )
                        } == true
                    }
                } catch (failure: WalletPayloadCorruptionException) {
                    false
                }
            }

            if (
                (
                    hasSentinelEntry ||
                        candidates.isNotEmpty() ||
                        hasExistingWalletRecords()
                    ) &&
                !authenticatedByExistingPayload
            ) {
                throw WalletSecureStorageUnavailableException(
                    "The wallet encryption key does not authenticate existing data",
                    kind = WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS
                )
            }

            persistPreferenceAesKeySentinel(walletPreferences, key.encoded)
        }
    }

    private fun requireExistingPinHealthy(
        allPreferences: Map<String, *>,
        key: ByteArray
    ) {
        if (!allPreferences.containsKey(WalletMasterKeyAttestation.PIN_CODE_KEY)) {
            return
        }

        val exactCiphertext =
            allPreferences[WalletMasterKeyAttestation.PIN_CODE_KEY] as? String
                ?: throw WalletSecureStorageUnavailableException(
                    "The encrypted wallet PIN is unavailable",
                    kind = WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS
                )
        val pin = try {
            decryptStrict(key, exactCiphertext)
        } catch (failure: WalletSecureStorageUnavailableException) {
            // A provider/JCA outage is global and retryable. Preserve the
            // classification produced by decryptStrict instead of presenting
            // temporary unavailability as irreversible wallet-key loss.
            throw failure
        } catch (failure: WalletPayloadCorruptionException) {
            throw WalletSecureStorageUnavailableException(
                "The encrypted wallet PIN cannot be authenticated",
                failure,
                WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS
            )
        }

        if (
            !WalletMasterKeyAttestation.isSemanticallyValidLegacyCandidate(
                key = WalletMasterKeyAttestation.PIN_CODE_KEY,
                plaintext = pin
            )
        ) {
            throw WalletSecureStorageUnavailableException(
                "The encrypted wallet PIN is invalid",
                kind = WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS
            )
        }
    }

    private fun persistPreferenceAesKeySentinel(
        preferences: android.content.SharedPreferences,
        key: ByteArray
    ) {
        val encrypted = MODERN_CIPHER_PREFIX + Base64.toBase64String(
            encryptModern(
                key = key,
                clear = WalletMasterKeyAttestation.SENTINEL_PLAINTEXT.toByteArray()
            )
        )
        latchKeyDurabilityFailure(
            message = "Unable to durably attest the wallet encryption key"
        ) {
            val committed = preferences.edit()
                .putString(WalletMasterKeyAttestation.SENTINEL_KEY, encrypted)
                .commit()
            check(
                committed &&
                    preferences.getString(
                        WalletMasterKeyAttestation.SENTINEL_KEY,
                        null
                    ) == encrypted &&
                    decryptStrict(key, encrypted) ==
                    WalletMasterKeyAttestation.SENTINEL_PLAINTEXT
            )
        }
    }

    private fun persistWrappedKeyDurably(
        preferences: android.content.SharedPreferences,
        wrappedKey: String
    ) {
        latchKeyDurabilityFailure(
            message = "Unable to durably store a new wallet encryption key"
        ) {
            val committed = preferences.edit()
                .putString(SECRET_KEY, wrappedKey)
                .commit()
            check(
                committed &&
                    preferences.getString(SECRET_KEY, null) == wrappedKey
            )
        }
    }

    private inline fun latchKeyDurabilityFailure(
        message: String,
        operation: () -> Unit
    ) {
        try {
            operation()
        } catch (failure: Throwable) {
            WalletSecureStorageHealth.latchProcessRestartRequired()
            throw WalletSecureStorageUnavailableException(
                message,
                failure,
                WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED
            )
        }
    }

    private fun hasExistingWalletRecords(): Boolean {
        val databaseFile = context.getDatabasePath(APP_DATABASE_NAME)
        if (!databaseFile.exists()) return false

        return try {
            SQLiteDatabase.openDatabase(
                databaseFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { database ->
                WALLET_ACCOUNT_TABLES.any { tableName ->
                    database.rawQuery(
                        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
                        arrayOf(tableName)
                    ).use { tableCursor ->
                        tableCursor.moveToFirst() &&
                            database.rawQuery(
                                "SELECT 1 FROM `$tableName` LIMIT 1",
                                null
                            ).use { rowCursor -> rowCursor.moveToFirst() }
                    }
                }
            }
        } catch (failure: Exception) {
            // An unreadable or locked existing database is not proof of a
            // fresh install. Fail closed instead of creating a replacement key.
            true
        }
    }

    fun isModernCiphertext(value: String?): Boolean {
        return value?.startsWith(MODERN_CIPHER_PREFIX) == true
    }

    private fun ensureKeystoreReady(allowCreate: Boolean) {
        if (privateKey != null && publicKey != null) return

        val loaded = loadWalletKeyMaterialWithRetry {
            var store = KeyStore.getInstance(KEY_STORE_PROVIDER).apply {
                load(null)
            }
            val existingPrivateKey = store.getKey(KEY_ALIAS, null) as? PrivateKey
            val existingPublicKey = store.getCertificate(KEY_ALIAS)?.publicKey
            PreferenceAesKeyPolicy.requireExistingWrappingKey(
                allowCreate = allowCreate,
                hasPrivateKey = existingPrivateKey != null,
                hasPublicKey = existingPublicKey != null
            )
            if (existingPrivateKey == null) {
                check(allowCreate) {
                    "The wallet wrapping key is missing"
                }
                createKeys()
                store = KeyStore.getInstance(KEY_STORE_PROVIDER).apply {
                    load(null)
                }
            }

            val loadedPrivateKey = store.getKey(KEY_ALIAS, null) as? PrivateKey
                ?: error("The wallet wrapping private key is unavailable")
            val loadedPublicKey = store.getCertificate(KEY_ALIAS)?.publicKey
                ?: error("The wallet wrapping public key is unavailable")
            WalletKeyMaterial(
                privateKey = loadedPrivateKey,
                publicKey = loadedPublicKey
            )
        }
        privateKey = loaded.privateKey
        publicKey = loaded.publicKey
    }

    private fun clearLoadedKeystore() {
        privateKey = null
        publicKey = null
    }

    private fun createKeys() {
        val startDate = Calendar.getInstance()
        val endDate = Calendar.getInstance()
        endDate.add(Calendar.YEAR, 25)

        val spec: AlgorithmParameterSpec

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            spec = KeyPairGeneratorSpec.Builder(context)
                .setAlias(KEY_ALIAS)
                .setSubject(X500Principal("CN=Sora"))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(startDate.time)
                .setEndDate(endDate.time)
                .build()
        } else {
            spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setCertificateSubject(X500Principal("CN=Sora"))
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(startDate.time)
                .setCertificateNotAfter(endDate.time)
                .build()
        }
        val keyPairGenerator = KeyPairGenerator.getInstance(RSA, KEY_STORE_PROVIDER)
        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    fun encrypt(cleartext: String?): String {
        if (cleartext != null && cleartext.isNotEmpty()) {
            try {
                return encrypt(getPrerenceAesKey().encoded, cleartext)
            } catch (failure: WalletSecureStorageUnavailableException) {
                throw failure
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return ""
    }

    fun encrypt(key: ByteArray, cleartext: String): String {
        return try {
            val encrypted = encryptModern(key, cleartext.toByteArray())
            MODERN_CIPHER_PREFIX + Base64.toBase64String(encrypted)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun decrypt(encryptedBase64: String): String {
        return decrypt(getPrerenceAesKey().encoded, encryptedBase64)
    }

    fun decrypt(key: ByteArray, encryptedBase64: String): String {
        return try {
            decryptStrict(key, encryptedBase64)
        } catch (failure: WalletPayloadCorruptionException) {
            ""
        }
    }

    private fun decryptStrict(key: ByteArray, encryptedBase64: String): String {
        if (encryptedBase64.length > MAX_ENCRYPTED_CIPHERTEXT_CHARS) {
            throw WalletPayloadCorruptionException(
                "Encrypted preference exceeds the safe decode limit"
            )
        }

        val result = if (isModernCiphertext(encryptedBase64)) {
            val payload = encryptedBase64.removePrefix(MODERN_CIPHER_PREFIX)
            val encrypted = decodePayloadBase64(payload)
            if (encrypted.size < GCM_IV_SIZE + GCM_TAG_SIZE_BYTES) {
                throw WalletPayloadCorruptionException(
                    "Authenticated ciphertext is truncated"
                )
            }
            decryptModern(key, encrypted)
        } else {
            val encrypted = decodePayloadBase64(encryptedBase64)
            if (encrypted.size < LEGACY_BLOCK_SIZE * 2) {
                throw WalletPayloadCorruptionException(
                    "Legacy ciphertext is truncated"
                )
            }
            decryptLegacy(key, encrypted)
        }

        return String(result, Charsets.UTF_8)
    }

    private fun decodePayloadBase64(encoded: String): ByteArray {
        return try {
            Base64.decode(encoded)
        } catch (failure: Exception) {
            throw WalletPayloadCorruptionException(
                "Encrypted preference is not valid Base64",
                failure
            )
        }
    }

    @Throws(Exception::class)
    private fun encryptModern(key: ByteArray, clear: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_SIZE)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, AES), GCMParameterSpec(GCM_TAG_SIZE_BITS, iv), secureRandom)

        return Arrays.concatenate(iv, cipher.doFinal(clear))
    }

    @Throws(
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        InvalidAlgorithmParameterException::class,
        InvalidKeyException::class,
        BadPaddingException::class,
        IllegalBlockSizeException::class
    )
    private fun decryptModern(key: ByteArray, encrypted: ByteArray): ByteArray {
        val iv = Arrays.copyOfRange(encrypted, 0, GCM_IV_SIZE)
        val cipherText = Arrays.copyOfRange(encrypted, GCM_IV_SIZE, encrypted.size)

        return decryptPayload(
            transformation = "AES/GCM/NoPadding",
            key = key,
            parameters = GCMParameterSpec(GCM_TAG_SIZE_BITS, iv),
            ciphertext = cipherText
        )
    }

    @Throws(Exception::class)
    private fun encryptLegacy(key: ByteArray, clear: ByteArray): ByteArray {
        val skeySpec = SecretKeySpec(key, AES)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, IvParameterSpec(generateLegacyIVBytes()), secureRandom)
        return Arrays.concatenate(cipher.iv, cipher.doFinal(clear))
    }

    @Throws(
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        InvalidAlgorithmParameterException::class,
        InvalidKeyException::class,
        BadPaddingException::class,
        IllegalBlockSizeException::class
    )
    private fun decryptLegacy(key: ByteArray, encrypted: ByteArray): ByteArray {
        return decryptPayload(
            transformation = "AES/CBC/PKCS5Padding",
            key = key,
            parameters = IvParameterSpec(
                Arrays.copyOfRange(encrypted, 0, LEGACY_BLOCK_SIZE)
            ),
            ciphertext = Arrays.copyOfRange(
                encrypted,
                LEGACY_BLOCK_SIZE,
                encrypted.size
            )
        )
    }

    private fun decryptPayload(
        transformation: String,
        key: ByteArray,
        parameters: AlgorithmParameterSpec,
        ciphertext: ByteArray
    ): ByteArray {
        return try {
            payloadDecryptor.decrypt(
                transformation = transformation,
                key = key,
                parameters = parameters,
                ciphertext = ciphertext,
                secureRandom = secureRandom
            )
        } catch (failure: WalletSecureStorageUnavailableException) {
            throw failure
        } catch (failure: BadPaddingException) {
            // AEADBadTagException is a BadPaddingException on Android. With an
            // already-attested key, either exception proves this one payload is
            // corrupt rather than showing a device-wide provider outage.
            throw WalletPayloadCorruptionException(
                "Encrypted preference authentication or padding is invalid",
                failure
            )
        } catch (failure: IllegalBlockSizeException) {
            throw WalletPayloadCorruptionException(
                "Encrypted preference has an invalid block size",
                failure
            )
        } catch (failure: Exception) {
            // NoSuchAlgorithm, NoSuchPadding, InvalidKey,
            // InvalidAlgorithmParameter, ProviderException, SecurityException,
            // and unknown provider failures are global. They must roll Room
            // back and must never be converted to per-wallet quarantine.
            throw WalletSecureStorageUnavailableException(
                "The wallet payload cipher is unavailable",
                failure
            )
        }
    }

    private fun encryptRsa(input: ByteArray): String {
        val wrappingKey = checkNotNull(publicKey) {
            "The wallet wrapping public key is unavailable"
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
        return Base64.toBase64String(cipher.doFinal(input))
    }

    private fun decryptExistingWrappedKey(encrypted: String): ByteArray {
        val wrappingKey = checkNotNull(privateKey) {
            "The wallet wrapping private key is unavailable"
        }
        val wrappedBytes = PreferenceAesKeyPolicy.decodeStoredWrappedKey(
            encoded = encrypted,
            decode = Base64::decode
        )

        val cipher = Cipher.getInstance(TRANSFORMATION)
        return try {
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey)
            cipher.doFinal(wrappedBytes)
        } catch (failure: Exception) {
            throw WalletSecureStorageUnavailableException(
                "The existing wallet encryption key cannot be unwrapped",
                failure,
                PreferenceAesKeyPolicy.classifyExistingWrappedKeyFailure(
                    failure
                )
            )
        }
    }

    private fun generateLegacyIVBytes(): ByteArray {
        val ivBytes = ByteArray(LEGACY_BLOCK_SIZE)
        secureRandom.nextBytes(ivBytes)
        return ivBytes
    }

}

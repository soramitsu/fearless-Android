package jp.co.soramitsu.common.data.storage.encrypt

import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
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
import org.bouncycastle.util.Arrays
import org.bouncycastle.util.encoders.Base64

class EncryptionUtil @Inject constructor(
    private val context: Context
) {

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
        private const val MODERN_CIPHER_PREFIX = "v2:"

        private var second = false

        private var privateKey: PrivateKey? = null
        private var publicKey: PublicKey? = null

        private const val SECRET_KEY = "secret_key"
        private val secureRandom = SecureRandom()
        private var keyStore: KeyStore? = null
    }

    init {
        initKeystore()
    }

    fun getPrerenceAesKey(): Key {
        val prefs = context.getSharedPreferences(KEY_ALIAS, Context.MODE_PRIVATE)
        val encryptedKey = prefs.getString(SECRET_KEY, "")

        if (encryptedKey.isNullOrEmpty()) {
            val keyGenerator = KeyGenerator.getInstance(AES)
            keyGenerator.init(AES_KEY_LENGTH, secureRandom)
            val secretKey = keyGenerator.generateKey()
            prefs.edit().putString(SECRET_KEY, encryptRsa(secretKey.encoded)).apply()
            return secretKey
        }

        val key = decryptRsa(encryptedKey)
        if (key == null || key.size !in setOf(16, 24, 32)) {
            val keyGenerator = KeyGenerator.getInstance(AES)
            keyGenerator.init(AES_KEY_LENGTH, secureRandom)
            val regeneratedKey = keyGenerator.generateKey()
            prefs.edit().putString(SECRET_KEY, encryptRsa(regeneratedKey.encoded)).apply()
            return regeneratedKey
        }

        return SecretKeySpec(key, AES)
    }

    fun isModernCiphertext(value: String?): Boolean {
        return value?.startsWith(MODERN_CIPHER_PREFIX) == true
    }

    private fun initKeystore() {
        try {
            keyStore = KeyStore.getInstance(KEY_STORE_PROVIDER)
            keyStore!!.load(null)

            if (keyStore!!.getKey(KEY_ALIAS, null) == null) {
                createKeys()
            }

            privateKey = keyStore!!.getKey(KEY_ALIAS, null) as PrivateKey
            publicKey = keyStore!!.getCertificate(KEY_ALIAS).publicKey
        } catch (e: Exception) {
            if (!second) {
                second = true
                initKeystore()
            }
            e.printStackTrace()
        }
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
        return try {
            decrypt(getPrerenceAesKey().encoded, encryptedBase64)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun decrypt(key: ByteArray, encryptedBase64: String): String {
        return try {
            if (isModernCiphertext(encryptedBase64)) {
                val payload = encryptedBase64.removePrefix(MODERN_CIPHER_PREFIX)
                val encrypted = Base64.decode(payload)
                val result = decryptModern(key, encrypted)
                String(result)
            } else {
                val encrypted = Base64.decode(encryptedBase64)
                val result = decryptLegacy(key, encrypted)
                String(result)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
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

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, AES), GCMParameterSpec(GCM_TAG_SIZE_BITS, iv), secureRandom)

        return cipher.doFinal(cipherText)
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
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, AES),
            IvParameterSpec(Arrays.copyOfRange(encrypted, 0, LEGACY_BLOCK_SIZE)),
            secureRandom
        )
        return cipher.doFinal(Arrays.copyOfRange(encrypted, LEGACY_BLOCK_SIZE, encrypted.size))
    }

    private fun encryptRsa(input: ByteArray): String {
        val cipher: Cipher

        try {
            cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            return Base64.toBase64String(cipher.doFinal(input))
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: NoSuchPaddingException) {
            e.printStackTrace()
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
        } catch (e: BadPaddingException) {
            e.printStackTrace()
        } catch (e: IllegalBlockSizeException) {
            e.printStackTrace()
        }

        return ""
    }

    private fun decryptRsa(encrypted: String): ByteArray? {
        val cipher: Cipher

        try {
            cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            return cipher.doFinal(Base64.decode(encrypted))
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: NoSuchPaddingException) {
            e.printStackTrace()
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
        } catch (e: BadPaddingException) {
            e.printStackTrace()
        } catch (e: IllegalBlockSizeException) {
            e.printStackTrace()
        }

        return null
    }

    private fun generateLegacyIVBytes(): ByteArray {
        val ivBytes = ByteArray(LEGACY_BLOCK_SIZE)
        secureRandom.nextBytes(ivBytes)
        return ivBytes
    }
}

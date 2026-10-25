package jp.co.soramitsu.fearless_utils.encrypt.json.coders.type

interface JsonTypeEncoder {

    val encryptionKeyGenerator: EncryptionKeyGenerator

    val encryptor: Encryptor

    class KeyGenerationResult(
        val encryptingPrefix: ByteArray,
        val encryptionKey: ByteArray
    )

    interface EncryptionKeyGenerator {

        fun generate(password: ByteArray): KeyGenerationResult
    }

    interface Encryptor {

        fun encrypt(
            keyGenerationResult: KeyGenerationResult,
            data: ByteArray
        ): ByteArray
    }
}

fun JsonTypeEncoder.encode(
    data: ByteArray,
    password: ByteArray
) = encryptor.encrypt(encryptionKeyGenerator.generate(password), data)

interface JsonTypeDecoder {

    val encryptionKeyGenerator: EncryptionKeyGenerator

    val decryptor: Decryptor

    class KeyGenerationResult(
        val encryptedData: ByteArray,
        val secret: ByteArray
    )

    interface EncryptionKeyGenerator {

        fun generate(encrypted: ByteArray, password: ByteArray): KeyGenerationResult
    }

    interface Decryptor {

        /**
         * @return null if secret is not correct. Decrypted data otherwise
         */
        fun decrypt(keyGenerationResult: KeyGenerationResult): ByteArray?
    }
}

fun JsonTypeDecoder.decode(
    encrypted: ByteArray,
    password: ByteArray
) = decryptor.decrypt(encryptionKeyGenerator.generate(encrypted, password))

interface JsonEncryptionKeyGenerator :
    JsonTypeDecoder.EncryptionKeyGenerator,
    JsonTypeEncoder.EncryptionKeyGenerator

interface JsonCryptor :
    JsonTypeEncoder.Encryptor,
    JsonTypeDecoder.Decryptor

package jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.keyGenerator

import jp.co.soramitsu.fearless_utils.encrypt.json.asLittleEndianBytes
import jp.co.soramitsu.fearless_utils.encrypt.json.asLittleEndianInt
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.JsonEncryptionKeyGenerator
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.JsonTypeDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.coders.type.JsonTypeEncoder
import jp.co.soramitsu.fearless_utils.encrypt.json.copyBytes
import org.bouncycastle.crypto.generators.SCrypt
import java.security.SecureRandom
import java.util.Random
import kotlin.math.pow

private val SALT_OFFSET = 0
private val SALT_SIZE = 32

private val N_OFFSET = SALT_OFFSET + SALT_SIZE
private val N_SIZE = 4

private val P_OFFSET = N_OFFSET + N_SIZE
private val P_SIZE = 4

private val R_OFFSET = P_OFFSET + P_SIZE
private val R_SIZE = 4

private val N = 2.0.pow(15.0).toInt()
private const val p = 1
private const val r = 8

private const val SCRYPT_KEY_SIZE = 32

object ScryptKeyGenerator : JsonEncryptionKeyGenerator {

    private val random: Random = SecureRandom()

    override fun generate(
        encrypted: ByteArray,
        password: ByteArray
    ): JsonTypeDecoder.KeyGenerationResult {
        val salt = encrypted.copyBytes(SALT_OFFSET, SALT_SIZE)
        val N = encrypted.copyBytes(N_OFFSET, N_SIZE).asLittleEndianInt()
        val p = encrypted.copyBytes(P_OFFSET, P_SIZE).asLittleEndianInt()
        val r = encrypted.copyBytes(R_OFFSET, R_SIZE).asLittleEndianInt()

        val encryptionSecret = SCrypt.generate(password, salt, N, r, p, SCRYPT_KEY_SIZE)

        return JsonTypeDecoder.KeyGenerationResult(
            secret = encryptionSecret,
            encryptedData = encrypted.copyOfRange(R_OFFSET + R_SIZE, encrypted.size)
        )
    }

    override fun generate(password: ByteArray): JsonTypeEncoder.KeyGenerationResult {
        val salt = generateSalt()

        val encryptionKey = SCrypt.generate(password, salt, N, r, p, SCRYPT_KEY_SIZE)

        return JsonTypeEncoder.KeyGenerationResult(
            encryptingPrefix = salt + N.asLittleEndianBytes() + p.asLittleEndianBytes() +
                r.asLittleEndianBytes(),
            encryptionKey = encryptionKey
        )
    }

    private fun generateSalt(): ByteArray {
        val bytes = ByteArray(SALT_SIZE)

        random.nextBytes(bytes)

        return bytes
    }
}

package jp.co.soramitsu.common.data.storage.envelope

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * The PIN's slow hash in the PIN wrap: Argon2id (BouncyCastle), stored with
 * the record so it can be tuned for new wraps without breaking old ones.
 *
 * 32 MiB, 2 passes, 1 lane. Measured with 3 passes: 0.37-0.50 s per PIN check
 * on an arm64 API 34 emulator on an Apple-silicon host. 2 passes keeps a
 * mid-range phone, roughly 2-3 times slower per core, near 0.5-1 s.
 */
data class WalletPinKdfParameters(
    val algorithm: Int = ARGON2ID_V13,
    val memoryKiB: Int = DEFAULT_MEMORY_KIB,
    val iterations: Int = DEFAULT_ITERATIONS,
    val parallelism: Int = DEFAULT_PARALLELISM
) {
    /** Bounds that keep a damaged or hostile record from exhausting memory or time. */
    val isAcceptable: Boolean
        get() = algorithm == ARGON2ID_V13 &&
            memoryKiB in MIN_MEMORY_KIB..MAX_MEMORY_KIB &&
            iterations in 1..MAX_ITERATIONS &&
            parallelism in 1..MAX_PARALLELISM &&
            memoryKiB >= 8 * parallelism

    companion object {
        const val ARGON2ID_V13 = 1
        const val DEFAULT_MEMORY_KIB = 32 * 1024
        const val DEFAULT_ITERATIONS = 2
        const val DEFAULT_PARALLELISM = 1
        const val MIN_MEMORY_KIB = 8
        const val MAX_MEMORY_KIB = 64 * 1024
        const val MAX_ITERATIONS = 16
        const val MAX_PARALLELISM = 4

        val DEFAULT = WalletPinKdfParameters()
    }
}

object WalletPinKdf {

    const val OUTPUT_BYTES = 32

    /** Argon2id of the PIN's ASCII digits. Zero the result after use. */
    fun derive(pin: String, salt: ByteArray, parameters: WalletPinKdfParameters): ByteArray {
        require(parameters.isAcceptable) { "Unsupported PIN KDF parameters" }
        require(salt.size >= MIN_SALT_BYTES) { "The PIN KDF salt is too short" }
        val password = pin.toByteArray(Charsets.US_ASCII)
        try {
            val generator = Argon2BytesGenerator()
            generator.init(
                Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withMemoryAsKB(parameters.memoryKiB)
                    .withIterations(parameters.iterations)
                    .withParallelism(parameters.parallelism)
                    .withSalt(salt)
                    .build()
            )
            val output = ByteArray(OUTPUT_BYTES)
            generator.generateBytes(password, output)
            return output
        } finally {
            password.fill(0)
        }
    }

    private const val MIN_SALT_BYTES = 16
}

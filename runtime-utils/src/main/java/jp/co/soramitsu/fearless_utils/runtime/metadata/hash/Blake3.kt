package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

import org.bouncycastle.crypto.digests.Blake3Digest

/** BLAKE3 with the default 256-bit output and no key, as RFC 78 uses it. */
object Blake3 {
    const val HASH_SIZE = 32

    fun hash256(data: ByteArray): ByteArray {
        val digest = Blake3Digest()
        digest.update(data, 0, data.size)
        val output = ByteArray(HASH_SIZE)
        digest.doFinal(output, 0)
        return output
    }
}

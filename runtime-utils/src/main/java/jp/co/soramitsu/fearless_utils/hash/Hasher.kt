package jp.co.soramitsu.fearless_utils.hash

import net.jpountz.xxhash.XXHashFactory
import org.bouncycastle.jcajce.provider.digest.Blake2b
import org.bouncycastle.jcajce.provider.digest.Keccak

object Hasher {
    private val blake2bLock = Any()

    private val blake2b256 = Blake2b.Blake2b256()

    private val blake2b128 = Blake2b128()

    private val blake2b512 = Blake2b.Blake2b512()

    val xxHash64 = XXHashFactory.safeInstance().hash64()
    val xxHash128 = XXHash(128, xxHash64)
    val xxHash256 = XXHash(256, xxHash64)

    fun ByteArray.xxHash64() = xxHash64.hash(this)
    fun ByteArray.xxHash128() = xxHash128.hash(this)
    fun ByteArray.xxHash256() = xxHash256.hash(this)

    fun ByteArray.blake2b128() = withBlake2bLock { blake2b128.digest(this) }
    fun ByteArray.blake2b256() = withBlake2bLock { blake2b256.digest(this) }
    fun ByteArray.blake2b512() = withBlake2bLock { blake2b512.digest(this) }

    fun ByteArray.keccak256(): ByteArray {
        val digest = Keccak.Digest256()

        return digest.digest(this)
    }

    fun ByteArray.blake2b128Concat() = withBlake2bLock { blake2b128.hashConcat(this) }

    private inline fun <T> withBlake2bLock(action: () -> T) = synchronized(blake2bLock, action)
}

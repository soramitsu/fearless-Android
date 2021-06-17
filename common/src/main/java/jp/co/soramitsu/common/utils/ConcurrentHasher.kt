package jp.co.soramitsu.common.utils

import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ConcurrentHasher {

    private val mutex = Mutex()

    suspend fun ByteArray.concurrentBlake2b256() = mutex.withLock { blake2b256() }
}

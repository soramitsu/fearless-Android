package jp.co.soramitsu.fearless_utils.hash

import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.random.Random

class HasherTest {

    @Test
    fun `blake2b should be thread safe`(): Unit = runBlocking {
        val testData = (0..1000).map { Random.nextBytes(32) }

        val sequentialResults = testData.map { it.blake2b256() }

        val concurrentResults = testData
            .map {
                // Lazy to ensure parallel start later for more concurrency
                async(Dispatchers.Default, CoroutineStart.LAZY) { it.blake2b256() }
            }.awaitAll()

        assert(
            sequentialResults
                .zip(concurrentResults)
                .all { (expected, actual) -> expected.contentEquals(actual) }
        )
    }
}
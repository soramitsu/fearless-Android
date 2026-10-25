package jp.co.soramitsu.staking.impl.data.repository

import java.io.IOException
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.common.data.network.runtime.binding.BinderWithKey
import jp.co.soramitsu.common.data.network.runtime.binding.BlockHash
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.storage.source.ChildKeyBuilder
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.StorageKey
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

/** Real feature repositories must retain the status-bearing runtime boundary. */
class PoolStorageStatusBoundaryTest {
    @Test fun `pool member repository preserves failed observation versus acknowledged absence`() = runTest {
        val h = Harness(); val values = h.pool.observePoolMembers("local", ByteArray(32)).toList()
        assertSame(h.storage.failure, values[0].exceptionOrNull())
        assertTrue(values[1].isSuccess); assertNull(values[1].getOrThrow())
        assertEquals(listOf("local"), h.storage.chains)
    }

    @Test fun `bonded pool repository preserves failed observation versus acknowledged absence`() = runTest {
        val h = Harness(); val values = h.pool.observePool("local", BigInteger.ONE).toList()
        assertSame(h.storage.failure, values[0].exceptionOrNull())
        assertTrue(values[1].isSuccess); assertNull(values[1].getOrThrow())
        assertEquals(listOf("local"), h.storage.chains)
    }

    @Test fun `nominations repository cannot convert failed observation into missing validators`() = runTest {
        val h = Harness(); val values = h.relay.observeRemoteAccountNominationsResult("local", ByteArray(32)).toList()
        assertSame(h.storage.failure, values[0].exceptionOrNull())
        assertTrue(values[1].isSuccess); assertNull(values[1].getOrThrow())
        assertEquals(listOf("local"), h.storage.chains)
    }

    @Test fun `metadata fallback requires acknowledged absence and propagates failed query`() = runTest {
        val h = Harness()
        try { h.pool.getPoolMetadata("local", BigInteger.ONE); fail("Unavailable metadata must remain unavailable") }
        catch (actual: IOException) { assertSame(h.storage.failure, actual) }
        h.storage.queryAnswer = Result.success(null)
        assertNull(h.pool.getPoolMetadata("local", BigInteger.ONE))
        assertEquals(listOf("local", "local"), h.storage.chains)
    }

    private class Harness {
        val storage = AcknowledgedStorage()
        val pool = StakingPoolDataSource(storage, mock(), mock(), mock(), mock())
        val relay = StakingRelayChainScenarioRepository(storage, mock(), mock(), mock(), mock(), mock())
    }

    private class AcknowledgedStorage : StorageDataSource {
        val failure = IOException("Local storage unavailable")
        private val runtime = mock<RuntimeSnapshot>()
        val chains = mutableListOf<String>()
        var queryAnswer: Result<String?> = Result.failure(failure)
        override fun <T> observeResult(chainId: String, keyBuilder: (RuntimeSnapshot) -> StorageKey?, binder: Binder<T>): Flow<Result<T>> {
            chains += chainId
            return flowOf(Result.failure<String?>(failure), Result.success(null)).map { result -> result.map { binder(it, runtime) } }
        }
        override suspend fun <T> queryResult(chainId: String, keyBuilder: (RuntimeSnapshot) -> StorageKey?, at: BlockHash?, binding: Binder<T>): Result<T> {
            chains += chainId
            return queryAnswer.map { binding(it, runtime) }
        }
        override suspend fun <T> query(chainId: String, keyBuilder: (RuntimeSnapshot) -> StorageKey?, at: BlockHash?, binding: Binder<T>): T = legacy()
        override suspend fun <K, T> queryKeys(chainId: String, keysBuilder: (RuntimeSnapshot) -> Map<StorageKey, K>, at: BlockHash?, binding: Binder<T>): Map<K, T> = legacy()
        override fun <T> observe(chainId: String, keyBuilder: (RuntimeSnapshot) -> StorageKey?, binder: Binder<T>): Flow<T> = legacy()
        override suspend fun <K, T> queryByPrefix(chainId: String, prefixKeyBuilder: (RuntimeSnapshot) -> StorageKey?, keyExtractor: (String) -> K, binding: BinderWithKey<T, K>): Map<K, T> = legacy()
        override suspend fun <T> queryChildState(chainId: String, storageKeyBuilder: (RuntimeSnapshot) -> StorageKey, childKeyBuilder: ChildKeyBuilder, binder: Binder<T>): T = legacy()
        private fun legacy(): Nothing = throw AssertionError("Pool repository must not erase storage status through a legacy API")
    }
}

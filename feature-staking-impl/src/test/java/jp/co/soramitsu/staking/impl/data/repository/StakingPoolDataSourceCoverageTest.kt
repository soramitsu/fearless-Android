package jp.co.soramitsu.staking.impl.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.Binder
import jp.co.soramitsu.common.data.network.runtime.binding.BinderWithKey
import jp.co.soramitsu.common.data.network.runtime.binding.BlockHash
import jp.co.soramitsu.common.utils.nominationPools
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.storage.source.ChildKeyBuilder
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.StorageKey
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nomination pool storage over the Polkadot Asset Hub runtime metadata: keys are built and values bound by the real
 * data source, from an in-memory storage holding SCALE values of the same runtime.
 */
class StakingPoolDataSourceCoverageTest {

    private val runtime: RuntimeSnapshot = AssetHubMetadataContractTest.runtimes.getValue("polkadot")
    private val pools = runtime.metadata.nominationPools()
    private val vectors: JsonObject = javaClass.getResourceAsStream("/asset-hub/polkadot-vectors.json")!!.reader().use {
        Gson().fromJson(it, JsonObject::class.java)
    }
    private val member = ByteArray(32) { 0x11 }

    private val remote = FakeStorage(runtime)
    private val rpcRegistry = mockk<IChainRegistry>()
    private val dataSource = StakingPoolDataSource(
        remote,
        FakeStorage(runtime),
        mockk<ChainRegistry>(),
        mockk<WalletConstants>(),
        RpcCalls(rpcRegistry)
    )

    private fun vector(name: String) = vectors[name].asString

    private fun plain(name: String) = pools.storage(name).storageKey()

    private fun key(name: String, vararg arguments: Any?) = pools.storage(name).storageKey(runtime, *arguments)

    @Suppress("UNCHECKED_CAST")
    private fun encode(name: String, value: Any): String = (pools.storage(name).returnType() as Type<Any>).toHex(runtime, value)

    @Test
    fun `pool bonds and counters are read from the hub runtime`() = runTest {
        remote.values[plain("MinJoinBond")] = encode("MinJoinBond", BigInteger.valueOf(10))
        remote.values[plain("MinCreateBond")] = encode("MinCreateBond", BigInteger.valueOf(500))
        remote.values[plain("CounterForBondedPools")] = encode("CounterForBondedPools", BigInteger.valueOf(42))
        remote.values[plain("LastPoolId")] = encode("LastPoolId", BigInteger.valueOf(77))

        assertEquals(BigInteger.valueOf(10), dataSource.minJoinBond(CHAIN))
        assertEquals(BigInteger.valueOf(500), dataSource.minCreateBond(CHAIN))
        assertEquals(BigInteger.valueOf(42), dataSource.existingPools(CHAIN))
        assertEquals(BigInteger.valueOf(42), dataSource.getPoolsCount(CHAIN))
        assertEquals(BigInteger.valueOf(77), dataSource.lastPoolId(CHAIN))
    }

    @Test
    fun `absent limits and counters use their defaults`() = runTest {
        assertNull(dataSource.maxPools(CHAIN))
        assertNull(dataSource.maxPoolMembers(CHAIN))
        assertNull(dataSource.maxMembersInPool(CHAIN))
        assertEquals(BigInteger.ZERO, dataSource.existingPools(CHAIN))
        assertEquals(BigInteger.ZERO, dataSource.getPoolsCount(CHAIN))
        assertEquals(BigInteger.ZERO, dataSource.lastPoolId(CHAIN))
        assertTrue(runCatching { dataSource.minCreateBond(CHAIN) }.isFailure)
    }

    @Test
    fun `optional limits are read when set`() = runTest {
        remote.values[plain("MaxPools")] = encode("MaxPools", BigInteger.valueOf(500))
        remote.values[plain("MaxPoolMembers")] = encode("MaxPoolMembers", BigInteger.valueOf(1_000))
        remote.values[plain("MaxPoolMembersPerPool")] = encode("MaxPoolMembersPerPool", BigInteger.valueOf(64))

        assertEquals(BigInteger.valueOf(500), dataSource.maxPools(CHAIN))
        assertEquals(BigInteger.valueOf(1_000), dataSource.maxPoolMembers(CHAIN))
        assertEquals(BigInteger.valueOf(64), dataSource.maxMembersInPool(CHAIN))
    }

    @Test
    fun `bonded pools and their names are read by prefix`() = runTest {
        remote.values[key("BondedPools", SEVEN)] = vector("bondedPool")
        remote.values[key("Metadata", SEVEN)] = NAME_SCALE

        val bonded = dataSource.bondedPools(CHAIN)
        val names = dataSource.poolsMetadata(CHAIN)

        assertEquals(setOf(SEVEN), bonded.keys)
        assertEquals(BigInteger.valueOf(5_000), bonded.getValue(SEVEN)!!.points)
        assertEquals(mapOf(SEVEN to "Fearless"), names)
    }

    @Test
    fun `single pool and member reads bind current storage`() = runTest {
        remote.values[key("BondedPools", SEVEN)] = vector("bondedPool")
        remote.values[key("PoolMembers", member)] = vector("poolMember")
        remote.values[key("Metadata", SEVEN)] = NAME_SCALE

        val pool = dataSource.observePool(CHAIN, SEVEN).first().getOrThrow()!!
        assertEquals(BigInteger.valueOf(3), pool.memberCounter)
        assertArrayEquals(member, pool.depositor)
        assertEquals(SEVEN, dataSource.poolMembers(CHAIN, member)!!.poolId)
        assertEquals(BigInteger.valueOf(1_000), dataSource.observePoolMembers(CHAIN, member).first().getOrThrow()!!.points)
        assertEquals("Fearless", dataSource.getPoolMetadata(CHAIN, SEVEN))
    }

    @Test
    fun `absent pool entries are null`() = runTest {
        assertNull(dataSource.getPoolMetadata(CHAIN, BigInteger.ONE))
        assertNull(dataSource.poolMembers(CHAIN, ByteArray(32) { 0x12 }))
        assertNull(dataSource.observePool(CHAIN, BigInteger.ONE).first().getOrThrow())
        assertNull(dataSource.observePoolRewards(CHAIN, SEVEN).first())
        assertTrue(dataSource.bondedPools(CHAIN).isEmpty())
    }

    @Test
    fun `pending rewards come from the runtime API`() = runTest {
        val socket = mockk<SocketService>()
        val connection = mockk<ChainConnection>()
        every { rpcRegistry.getConnection(CHAIN) } returns connection
        every { connection.socketService } returns socket
        every { socket.executeRequest(any(), any(), any()) } answers {
            thirdArg<SocketService.ResponseListener<RpcResponse>>().onNext(RpcResponse("2.0", PENDING_FIVE, 1, null))
            mockk<SocketService.Cancellable>(relaxed = true)
        }

        assertEquals(BigInteger.valueOf(5), dataSource.getPendingRewards(CHAIN, member).getOrThrow())

        every { rpcRegistry.getConnection(CHAIN) } throws IllegalStateException("offline")
        assertTrue(dataSource.getPendingRewards(CHAIN, member).isFailure)
    }

    /** In-memory storage: builds keys and binds values with the given runtime, as the real sources do. */
    private class FakeStorage(private val runtime: RuntimeSnapshot) : StorageDataSource {
        val values = mutableMapOf<String, String?>()

        private fun valueOf(key: String?) = key?.let { values[it] }

        override suspend fun <T> query(chainId: String, keyBuilder: (RuntimeSnapshot) -> StorageKey?, at: BlockHash?, binding: Binder<T>): T =
            binding(valueOf(keyBuilder(runtime)), runtime)

        override suspend fun <K, T> queryKeys(
            chainId: String,
            keysBuilder: (RuntimeSnapshot) -> Map<StorageKey, K>,
            at: BlockHash?,
            binding: Binder<T>
        ): Map<K, T> = keysBuilder(runtime).entries.associate { (key, argument) -> argument to binding(values[key], runtime) }

        override fun <T> observe(chainId: String, keyBuilder: (RuntimeSnapshot) -> StorageKey?, binder: Binder<T>): Flow<T> =
            flow { emit(binder(valueOf(keyBuilder(runtime)), runtime)) }

        override suspend fun <T> queryResult(chainId: String, keyBuilder: (RuntimeSnapshot) -> StorageKey?, at: BlockHash?, binding: Binder<T>): Result<T> =
            runCatching { query(chainId, keyBuilder, at, binding) }

        override fun <T> observeResult(chainId: String, keyBuilder: (RuntimeSnapshot) -> StorageKey?, binder: Binder<T>): Flow<Result<T>> =
            observe(chainId, keyBuilder, binder).map { Result.success(it) }

        override suspend fun <K, T> queryByPrefix(
            chainId: String,
            prefixKeyBuilder: (RuntimeSnapshot) -> StorageKey?,
            keyExtractor: (String) -> K,
            binding: BinderWithKey<T, K>
        ): Map<K, T> {
            val prefix = prefixKeyBuilder(runtime) ?: return emptyMap()
            return values.filterKeys { it.startsWith(prefix) }.entries.associate { (key, scale) ->
                val extracted = keyExtractor(key)
                extracted to binding(scale, runtime, extracted)
            }
        }

        override suspend fun <T> queryChildState(
            chainId: String,
            storageKeyBuilder: (RuntimeSnapshot) -> StorageKey,
            childKeyBuilder: ChildKeyBuilder,
            binder: Binder<T>
        ): T = error("Child state is not read by the pool data source")
    }

    private companion object {
        const val CHAIN = "pool-chain"
        val SEVEN: BigInteger = BigInteger.valueOf(7)

        /** SCALE bytes of "Fearless": compact length 8, then the UTF-8 bytes. */
        const val NAME_SCALE = "0x20466561726c657373"

        /** A u128 of 5, little-endian. */
        const val PENDING_FIVE = "0x05000000000000000000000000000000"
    }
}

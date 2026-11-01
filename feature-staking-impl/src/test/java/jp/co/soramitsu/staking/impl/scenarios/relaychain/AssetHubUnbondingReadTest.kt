package jp.co.soramitsu.staking.impl.scenarios.relaychain

import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.staking.impl.data.repository.AssetHubMetadataContractTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException

/**
 * A nominator whom the runtime does not slash unbonds fast unless it was a validator in the recent eras
 * (`LastValidatorEra`). The reads go through the real remote storage source and a local socket here: a read that
 * fails is an error, which the interactor shows as the full bonding duration, and never "no validator history" with
 * the fast period.
 */
class AssetHubUnbondingReadTest {
    private val runtime = AssetHubMetadataContractTest.runtimes.getValue("polkadot")
    private val staking = runtime.metadata.staking()
    private val stash = ByteArray(32) { 6 }
    private val block = "0x" + "ab".repeat(32)
    private val socket = mock<SocketService>()
    private val connection = mock<ChainConnection> { on { socketService } doReturn socket }
    private val registry = mock<ChainRegistry>()
    private val values = mutableMapOf<String, String?>()
    private var failing: String? = null
    private val repository =
        StakingRelayChainScenarioRepository(RemoteStorageSource(registry, BulkRetriever()), mock(), registry, mock(), mock(), mock())
    private val full = staking.numberConstant("BondingDuration", runtime)
    private val fast = staking.numberConstant("NominatorFastUnbondDuration", runtime)

    @Suppress("UNCHECKED_CAST")
    private fun put(name: String, value: Any, vararg arguments: Any) {
        val storage = staking.storage(name)
        values[storage.storageKey(runtime, *arguments)] = (storage.returnType() as Type<Any>).toHex(runtime, value)
    }

    private suspend fun setUp() {
        whenever(registry.getRuntime(AssetHubStaking.POLKADOT)).thenReturn(runtime)
        whenever(registry.awaitConnection(AssetHubStaking.POLKADOT)).thenReturn(connection)
        whenever(socket.jsonMapper).thenReturn(Gson())
        whenever(socket.executeRequest(any(), any(), any())).thenAnswer { call ->
            val request = call.getArgument<RuntimeRequest>(0)
            val callback = call.getArgument<SocketService.ResponseListener<RpcResponse>>(2)
            @Suppress("UNCHECKED_CAST")
            val keys = request.params[0] as List<String>
            if (failing in keys) {
                callback.onError(IOException("Local node dropped the read"))
            } else {
                val answer = listOf(mapOf("block" to block, "changes" to keys.map { listOf(it, values[it]) }))
                callback.onNext(RpcResponse("2.0", answer, request.id, null))
            }
            mock<SocketService.Cancellable>()
        }
        put("AreNominatorsSlashable", false)
        put("ActiveEra", Struct.Instance(mapOf("index" to 100.toBigInteger(), "start" to 1000.toBigInteger())))
    }

    @Test
    fun `a failed validator history read is an error, never the fast period`() = runTest {
        setUp()
        failing = staking.storage("LastValidatorEra").storageKey(runtime, stash)
        val failure = runCatching { repository.assetHubUnbondingEras(AssetHubStaking.POLKADOT, stash) }.exceptionOrNull()
        assertTrue(failure.toString(), failure is IOException)
    }

    @Test
    fun `a failed slashability read is an error as well`() = runTest {
        setUp()
        failing = staking.storage("AreNominatorsSlashable").storageKey()
        assertTrue(runCatching { repository.assetHubUnbondingEras(AssetHubStaking.POLKADOT, stash) }.isFailure)
    }

    @Test
    fun `an acknowledged empty validator history gives the fast period`() = runTest {
        setUp()
        assertTrue(fast < full)
        assertEquals(fast, repository.assetHubUnbondingEras(AssetHubStaking.POLKADOT, stash))
    }

    @Test
    fun `a recent validator keeps the full period`() = runTest {
        setUp()
        put("LastValidatorEra", 99.toBigInteger(), stash)
        assertEquals(full, repository.assetHubUnbondingEras(AssetHubStaking.POLKADOT, stash))
    }
}

package jp.co.soramitsu.runtime.multiNetwork.runtime

import com.google.gson.Gson
import jp.co.soramitsu.common.utils.md5
import jp.co.soramitsu.fearless_utils.runtime.metadata.GetMetadataRequest
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection
import jp.co.soramitsu.runtime.multiNetwork.runtime.types.TypesFetcher
import jp.co.soramitsu.test_shared.any
import jp.co.soramitsu.test_shared.eq
import jp.co.soramitsu.test_shared.whenever
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

private const val TEST_TYPES = "Stub"

@RunWith(MockitoJUnitRunner::class)
class RuntimeSyncServiceTest {

    private val testChain by lazy {
        Mocks.chain(id = "1")
    }

    @Mock
    private lateinit var socket: SocketService

    @Mock
    private lateinit var testConnection: ChainConnection

    @Mock
    private lateinit var typesFetcher: TypesFetcher

    @Mock
    private lateinit var runtimeFilesCache: RuntimeFilesCache

    private lateinit var service: RuntimeSyncService

    @Before
    fun setup() = runBlocking {
        whenever(testConnection.socketService).thenReturn(socket)
        whenever(socket.jsonMapper).thenReturn(Gson())
        whenever(typesFetcher.getTypes(any())).thenReturn(TEST_TYPES)
        socketAnswersRequest(GetMetadataRequest, "Stub")


        service = RuntimeSyncService(typesFetcher, runtimeFilesCache)
    }

    @Test
    fun `should not start syncing new chain`() {
        service.registerChain(chain = testChain, connection = testConnection)

        assertFalse(service.isSyncing(testChain.id))
    }

    @Test
    fun `should start syncing on runtime version apply`() {
        service.registerChain(chain = testChain, connection = testConnection)

        service.applyRuntimeVersion(testChain.id)

        assertTrue(service.isSyncing(testChain.id))
    }

    @Test
    fun `should not start syncing the same chain`() {
        runBlocking {
            service.registerChain(chain = testChain, connection = testConnection)
            service.applyRuntimeVersion(testChain.id)

            service.awaitSync(testChain.id)

            assertFalse("isSyncing returns false after sync is finished", service.isSyncing(testChain.id))

            service.registerChain(chain = testChain, connection = testConnection)

            assertFalse(service.isSyncing(testChain.id))
        }
    }

    @Test
    fun `should sync modified chain`() {
        runBlocking {
            val newChain = Mockito.mock(Chain::class.java)
            whenever(newChain.id).thenAnswer { testChain.id }
            whenever(newChain.types).thenReturn(Chain.Types(url = "Changed", overridesCommon = false))

            service.registerChain(chain = testChain, connection = testConnection)
            service.applyRuntimeVersion(testChain.id)

            service.awaitSync(testChain.id)

            // since neither types nor metadata will be syncing, it may finish faster than test will be able to call awaitSync
            // so, listen for sync changes before executing sync
            val syncResultFlow = service.syncResultFlow(testChain.id)
                .shareIn(GlobalScope, started = SharingStarted.Eagerly, replay = 1)

            service.registerChain(chain = newChain, connection = testConnection)

            assertTrue(service.isSyncing(testChain.id))

            val syncResult = syncResultFlow.first()

            assertNull("Metadata should not sync", syncResult.metadataHash)
        }
    }

    @Test
    fun `should sync types when url is not null`() {
        runBlocking {
            whenever(testChain.types).thenReturn(Chain.Types("Stub", overridesCommon = false))

            service.registerChain(chain = testChain, connection = testConnection)
            service.applyRuntimeVersion(testChain.id)

            val result = service.awaitSync(testChain.id)

            assertNotNull(result.typesHash)
        }
    }

    @Test
    fun `should not sync types when url is null`() {
        runBlocking {
            service.registerChain(chain = testChain, connection = testConnection)
            service.applyRuntimeVersion(testChain.id)

            val result = service.awaitSync(testChain.id)

            assertNull(result.typesHash)
        }
    }

    @Test
    fun `should cancel syncing when chain is unregistered`() {
        runBlocking {
            service.registerChain(chain = testChain, connection = testConnection)
            service.applyRuntimeVersion(testChain.id)

            assertTrue(service.isSyncing(testChain.id))

            service.unregisterChain(testChain.id)

            assertFalse(service.isSyncing(testChain.id))
        }
    }

    @Test
    fun `should broadcast sync result`() {
        runBlocking {
            whenever(testChain.types).thenReturn(Chain.Types("testUrl", overridesCommon = false))
            service.registerChain(chain = testChain, connection = testConnection)
            service.applyRuntimeVersion(testChain.id)

            val result = service.awaitSync(testChain.id)

            assertEquals(TEST_TYPES.md5(), result.typesHash)
        }
    }

    private suspend fun RuntimeSyncService.awaitSync(chainId: String) = syncResultFlow(chainId).first()

    private fun socketAnswersRequest(request: RuntimeRequest, response: Any?) {
        whenever(socket.executeRequest(eq(request), deliveryType = any(), callback = any())).thenAnswer {
            (it.arguments[2] as SocketService.ResponseListener<RpcResponse>).onNext(RpcResponse(jsonrpc = "2.0", response, id = 1, error = null))

            object : SocketService.Cancellable {
                override fun cancel() {
                    // pass
                }
            }
        }
    }
}

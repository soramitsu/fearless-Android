package jp.co.soramitsu.runtime.multiNetwork.runtime

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.utils.md5
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainRuntimeInfoLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.types.TypesFetcher
import jp.co.soramitsu.shared_utils.runtime.metadata.GetMetadataRequest
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.shared_utils.wsrpc.response.RpcResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

private const val TEST_TYPES = "Stub"

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeSyncServiceTest {

    private val testChain by lazy {
        Mocks.chain(id = "1")
    }

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var socket: SocketService

    @MockK
    private lateinit var connectionPool: ConnectionPool

    @MockK
    private lateinit var testConnection: ChainConnection

    @MockK
    private lateinit var typesFetcher: TypesFetcher

    @MockK
    private lateinit var chainDao: ChainDao

    @MockK
    private lateinit var gson: Gson

    @MockK
    private lateinit var runtimeFilesCache: RuntimeFilesCache

    private lateinit var service: RuntimeSyncService

    @Before
    fun setup() = runTest {
        every { testConnection.socketService } returns socket
        every { socket.jsonMapper } returns gson
        coEvery { typesFetcher.getTypes(any()) } returns TEST_TYPES
        socketAnswersRequest(GetMetadataRequest, "Stub")

        service = RuntimeSyncService(typesFetcher, runtimeFilesCache, chainDao, 15, connectionPool)
    }

    @Test
    @Ignore
    fun `should not start syncing new chain`() = runTest {
        service.registerChain(chain = testChain)

        assertFalse(service.isSyncing(testChain.id))
    }

    @Test
    @Ignore
    fun `should start syncing on runtime version apply`() = runTest {
        service.registerChain(chain = testChain)

        service.applyRuntimeVersion(testChain.id)

        assertTrue(service.isSyncing(testChain.id))
    }

    @Test
    @Ignore
    fun `should not start syncing the same chain`() = runTest {
        chainDaoReturnsUnsyncedRuntimeInfo()

        service.registerChain(chain = testChain)
        service.applyRuntimeVersion(testChain.id)

        service.awaitSync(testChain.id)

        assertFalse(
            "isSyncing returns false after sync is finished",
            service.isSyncing(testChain.id)
        )

        service.registerChain(chain = testChain)

        assertFalse(service.isSyncing(testChain.id))
    }

    @Test
    @Ignore
    fun `should sync modified chain`() = runTest {
        chainDaoReturnsUnsyncedRuntimeInfo()

        val newChain = mockk<Chain>()
        every { newChain.id } returns testChain.id

        service.registerChain(chain = testChain)
        service.applyRuntimeVersion(testChain.id)

        service.awaitSync(testChain.id)

        chainDaoReturnsSyncedRuntimeInfo()

        // since neither types nor metadata will be syncing, it may finish faster than test will be able to call awaitSync
        // so, listen for sync changes before executing sync
        val syncResultFlow = service.syncResultFlow(testChain.id)
            .shareIn(this, started = SharingStarted.Eagerly, replay = 1)

        service.registerChain(chain = newChain)

        assertTrue(service.isSyncing(testChain.id))

        val syncResult = syncResultFlow.first()

        assertNull("Metadata should not sync", syncResult.metadataHash)
    }

    @Test
    @Ignore
    fun `should sync types when url is not null`() = runTest {
        chainDaoReturnsSyncedRuntimeInfo()

        service.registerChain(chain = testChain)
        service.applyRuntimeVersion(testChain.id)

        val result = service.awaitSync(testChain.id)

        assertNotNull(result.typesHash)
    }

    @Test
    @Ignore
    fun `should not sync types when url is null`() = runTest {
        chainDaoReturnsUnsyncedRuntimeInfo()

        service.registerChain(chain = testChain)
        service.applyRuntimeVersion(testChain.id)

        val result = service.awaitSync(testChain.id)

        assertNull(result.typesHash)
    }

    @Test
    @Ignore
    fun `should cancel syncing when chain is unregistered`() = runTest {
        chainDaoReturnsUnsyncedRuntimeInfo()

        service.registerChain(chain = testChain)
        service.applyRuntimeVersion(testChain.id)

        assertTrue(service.isSyncing(testChain.id))

        service.unregisterChain(testChain.id)

        assertFalse(service.isSyncing(testChain.id))
    }

    @Test
    @Ignore
    fun `should broadcast sync result`() = runTest {
        chainDaoReturnsUnsyncedRuntimeInfo()

        service.registerChain(chain = testChain)
        service.applyRuntimeVersion(testChain.id)

        val result = service.awaitSync(testChain.id)

        assertEquals(TEST_TYPES.md5(), result.typesHash)
    }

    @Test
    @Ignore
    fun `should sync new version of metadata`() = runTest {
        chainDaoReturnsUnsyncedRuntimeInfo()

        service.registerChain(chain = testChain)
        service.applyRuntimeVersion(testChain.id)

        val syncResult = service.awaitSync(testChain.id)

        assertNotNull(syncResult.metadataHash)
    }

    @Test
    @Ignore
    fun `should not sync the same version of metadata`() = runTest {
        chainDaoReturnsSyncedRuntimeInfo()

        service.registerChain(chain = testChain)
        service.applyRuntimeVersion(testChain.id)

        val syncResult = service.awaitSync(testChain.id)

        assertNull(syncResult.metadataHash)
    }

    private fun chainDaoReturnsUnsyncedRuntimeInfo() {
        chainDaoReturnsRuntimeInfo(remoteVersion = 1, syncedVersion = 0)
    }

    private fun chainDaoReturnsSyncedRuntimeInfo() {
        chainDaoReturnsRuntimeInfo(remoteVersion = 1, syncedVersion = 1)
    }

    private fun chainDaoReturnsRuntimeInfo(remoteVersion: Int, syncedVersion: Int) {
        coEvery { chainDao.runtimeInfo(any()) } returns ChainRuntimeInfoLocal(
            "1",
            syncedVersion,
            remoteVersion
        )
    }

    private suspend fun RuntimeSyncService.awaitSync(chainId: String) =
        syncResultFlow(chainId).first()

    private fun socketAnswersRequest(request: RuntimeRequest, response: Any?) {
        val slot = slot<SocketService.ResponseListener<RpcResponse>>()
        every { socket.executeRequest(request, any(), capture(slot)) } answers {
            slot.captured.onNext(
                RpcResponse(
                    jsonrpc = "2.0",
                    response,
                    id = 1,
                    error = null
                )
            )
            object : SocketService.Cancellable {
                override fun cancel() {
                    // pass
                }
            }
        }
    }
}

package jp.co.soramitsu.runtime.multiNetwork.connection

import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.testshared.whenever
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import org.web3j.protocol.websocket.WebSocketService

@RunWith(MockitoJUnitRunner::class)
class EthereumConnectionTest {

//    @Mock
//    lateinit var webSocketService: WebSocketService

    @Mock
    lateinit var socketFactory: EthereumWebSocketFactory

    private val httpNodes = listOf(
        ChainNode(
            url = "https://eth-sepolia.blastapi.io/",
            name = "Sepolia node",
            isActive = true,
            isDefault = true
        ),
        ChainNode(
            url = "https://bsc-testnet.blastapi.io/",
            name = "Bsc testnet node",
            isActive = true,
            isDefault = true
        )
    )

    private val wssNodes = listOf(
        ChainNode(
            url = "wss://eth-sepolia.blastapi.io/",
            name = "Sepolia wss node",
            isActive = true,
            isDefault = true
        ),
        ChainNode(
            url = "wss://bsc-testnet.blastapi.io/",
            name = "Bsc wss testnet node",
            isActive = true,
            isDefault = true
        )
    )

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        Dispatchers.setMain(mainThreadSurrogate)
    }

    @Test
    fun `should throw exception when there are no nodes`() {
        val chain = mock(Chain::class.java)
        whenever(chain.nodes).thenReturn(listOf())
        val connectionResult =
            kotlin.runCatching {
                EthereumWebSocketConnection(
                    chain,
                    socketFactory,
                    { true },
                    { _, _ -> })
            }

        assertNotNull(connectionResult.requireException())
    }

    @Test
    fun `should throw exception when there are no wss nodes`() {
        val chain = mock(Chain::class.java)
        whenever(chain.nodes).thenReturn(httpNodes)
        whenever(chain.name).thenReturn("Ethereum")
        whenever(chain.id).thenReturn("1")
        val connectionResult =
            kotlin.runCatching {
                EthereumWebSocketConnection(
                    chain,
                    socketFactory,
                    { true },
                    { _, _ -> })
            }

        assert(connectionResult.requireException() is MissingWssNodesException)
    }

    @Test
    fun `should not throw exception when there are at least one wss node`() {
        val chain = mock(Chain::class.java)

        whenever(chain.nodes).thenReturn(wssNodes)

        val connectionResult =
            kotlin.runCatching {
                EthereumWebSocketConnection(
                    chain,
                    socketFactory,
                    { true },
                    { _, _ -> })
            }

        assertNotNull(connectionResult.requireValue())
    }

    @Test
    fun `should connect when there are at least one wss node`() = runTest {
        val chain = mock(Chain::class.java)

        whenever(chain.nodes).thenReturn(wssNodes)

        val connection = EthereumWebSocketConnection(chain, socketFactory, { true }, { _, _ -> })
        // todo fix it
        val webSocketService = WebSocketService(wssNodes.first().url, false)
        whenever(socketFactory.create(wssNodes.first().url)).thenReturn(webSocketService)
        whenever(webSocketService.connect()).thenReturn(Unit)

        val result = connection.connect()

        require(result.isSuccess)
    }

}

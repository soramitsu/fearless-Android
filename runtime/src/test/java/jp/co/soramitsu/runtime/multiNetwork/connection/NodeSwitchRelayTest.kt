package jp.co.soramitsu.runtime.multiNetwork.connection

import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.utils.second
import jp.co.soramitsu.core.models.ChainNode
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class NodeSwitchRelayTest {

    private val nodes = listOf(
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
        ),
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

    @Test
    fun `should connect to the first node if connection result is ok`() {
        runBlocking {
            val relay = NodesSwitchRelay(nodes)
            val firstNode = nodes.first()
            val connectionResult = relay {
                if (it.url == firstNode.url) {
                    Result.success("")
                } else {
                    Result.failure("")
                }
            }

            require(connectionResult.isSuccess)
            require(connectionResult.requireValue().url == firstNode.url)
        }
    }

    @Test
    fun `should switch to the second node if first node is bad`() {
        runBlocking {
            val relay = NodesSwitchRelay(nodes)
            val firstNode = nodes.first()
            val secondNode = nodes.second()
            val connectionResult = relay {
                return@relay if (it.url == firstNode.url) {
                    Result.failure("")
                }
                else {
                    Result.success("")
                }
            }

            require(connectionResult.isSuccess)
            require(connectionResult.requireValue().url == secondNode.url)
        }
    }

    @Test
    fun `should return failure if all nodes are bad`() {
        runBlocking {
            val relay = NodesSwitchRelay(nodes)
            val connectionResult = relay {
                hashCode()
                return@relay Result.failure("")
            }

            require(connectionResult.isFailure)
        }
    }
}